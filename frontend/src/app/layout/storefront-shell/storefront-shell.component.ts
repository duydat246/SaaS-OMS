import { DOCUMENT, NgTemplateOutlet } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed, toSignal } from "@angular/core/rxjs-interop";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from "@angular/router";
import { catchError, combineLatest, filter, map, Observable, of, startWith, Subject, switchMap, tap } from "rxjs";
import { StorefrontShop } from "@features/storefront/models/storefront.model";
import { StorefrontService } from "@features/storefront/services/storefront.service";
import {
  isNotFoundError,
  isValidStorefrontSlug,
  STOREFRONT_FONT_LINK_ID,
  STOREFRONT_FONT_URL,
  storefrontErrorMessage,
  telLink,
  zaloLink,
} from "@features/storefront/constants/storefront.constants";
import { StorefrontNotFoundComponent } from "@features/storefront/pages/storefront-not-found/storefront-not-found.component";

type ShellView = "loading" | "ready" | "not-found" | "error";
type ShopResult = { shop: StorefrontShop } | { notFound: true } | { error: string };

const APP_TITLE = "SaaS OMS";

/** Nhãn mono trên thanh điều hướng theo trang con đang mở: `/s/<slug>` hoặc `/s/<slug>/p/<id>`. */
function sectionLabel(router: Router): string | null {
  const segments = router.parseUrl(router.url).root.children["primary"]?.segments ?? [];
  if (segments.length === 2) {
    return "01 / Sản phẩm";
  }
  return segments.length === 4 && segments[2].path === "p" ? "02 / Chi tiết" : null;
}

@Component({
  selector: "app-storefront-shell",
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink, RouterOutlet, StorefrontNotFoundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./storefront-shell.component.html",
  styleUrls: ["../../features/storefront/styles/storefront-base.scss", "./storefront-shell.component.scss"],
})
export class StorefrontShellComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly storefront = inject(StorefrontService);
  private readonly title = inject(Title);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  private readonly retry$ = new Subject<void>();

  readonly view = signal<ShellView>("loading");
  readonly errorMessage = signal("");
  readonly shop = this.storefront.currentShop;
  readonly contact = computed(() => {
    const phone = this.shop()?.contactPhone;
    return phone ? { phone, tel: telLink(phone), zalo: zaloLink(phone) } : null;
  });
  readonly section = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => sectionLabel(this.router)),
    ),
    { initialValue: sectionLabel(this.router) },
  );
  readonly year = new Date().getFullYear();

  constructor() {
    this.loadFonts();

    const slug$ = this.route.paramMap.pipe(map((params) => params.get("slug")));

    combineLatest([slug$, this.retry$.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          // Đổi view trước: outlet bị gỡ nên trang con của shop cũ không gọi API với slug mới.
          this.view.set("loading");
          this.storefront.currentShop.set(null);
        }),
        switchMap(([slug]) => this.loadShop(slug)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => this.applyResult(result));

    this.destroyRef.onDestroy(() => {
      this.storefront.currentShop.set(null);
      this.title.setTitle(APP_TITLE);
    });
  }

  retry(): void {
    this.retry$.next();
  }

  /**
   * Chèn stylesheet font một lần vào `<head>`. Giữ lại khi rời trang khách để lần sau không tải lại;
   * admin không bị ảnh hưởng vì chỉ token của trang khách dùng các font này. Không tải được thì dùng font hệ thống.
   */
  private loadFonts(): void {
    if (this.document.getElementById(STOREFRONT_FONT_LINK_ID)) {
      return;
    }
    const link = this.document.createElement("link");
    link.id = STOREFRONT_FONT_LINK_ID;
    link.rel = "stylesheet";
    link.href = STOREFRONT_FONT_URL;
    this.document.head.appendChild(link);
  }

  private loadShop(slug: string | null): Observable<ShopResult> {
    if (!isValidStorefrontSlug(slug)) {
      return of({ notFound: true as const });
    }
    return this.storefront.getShop(slug).pipe(
      map((shop) => ({ shop })),
      catchError((err: unknown) =>
        of<ShopResult>(
          isNotFoundError(err)
            ? { notFound: true }
            : { error: storefrontErrorMessage(err, "Không tải được cửa hàng, vui lòng thử lại.") },
        ),
      ),
    );
  }

  private applyResult(result: ShopResult): void {
    if ("shop" in result) {
      this.storefront.currentShop.set(result.shop);
      this.title.setTitle(result.shop.name);
      this.view.set("ready");
    } else if ("notFound" in result) {
      this.view.set("not-found");
    } else {
      this.errorMessage.set(result.error);
      this.title.setTitle(APP_TITLE);
      this.view.set("error");
    }
  }
}
