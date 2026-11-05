import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, RouterLink, RouterOutlet } from "@angular/router";
import { catchError, combineLatest, map, Observable, of, startWith, Subject, switchMap, tap } from "rxjs";
import { StorefrontShop } from "@features/storefront/models/storefront.model";
import { StorefrontService } from "@features/storefront/services/storefront.service";
import {
  isNotFoundError,
  isValidStorefrontSlug,
  storefrontErrorMessage,
  telLink,
  zaloLink,
} from "@features/storefront/constants/storefront.constants";
import { StorefrontNotFoundComponent } from "@features/storefront/pages/storefront-not-found/storefront-not-found.component";

type ShellView = "loading" | "ready" | "not-found" | "error";
type ShopResult = { shop: StorefrontShop } | { notFound: true } | { error: string };

const APP_TITLE = "SaaS OMS";

@Component({
  selector: "app-storefront-shell",
  standalone: true,
  imports: [RouterLink, RouterOutlet, StorefrontNotFoundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./storefront-shell.component.html",
  styleUrls: ["./storefront-shell.component.scss"],
})
export class StorefrontShellComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly storefront = inject(StorefrontService);
  private readonly title = inject(Title);
  private readonly destroyRef = inject(DestroyRef);

  private readonly retry$ = new Subject<void>();

  readonly view = signal<ShellView>("loading");
  readonly errorMessage = signal("");
  readonly shop = this.storefront.currentShop;
  readonly contact = computed(() => {
    const phone = this.shop()?.contactPhone;
    return phone ? { phone, tel: telLink(phone), zalo: zaloLink(phone) } : null;
  });

  constructor() {
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
