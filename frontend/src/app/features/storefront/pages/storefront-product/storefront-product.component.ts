import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { catchError, combineLatest, map, Observable, of, startWith, Subject, switchMap, tap } from "rxjs";
import { StorefrontProductDetail, StorefrontSku } from "@features/storefront/models/storefront.model";
import { StorefrontService } from "@features/storefront/services/storefront.service";
import {
  formatStorefrontPrice,
  formatStorefrontPriceRange,
  isNotFoundError,
  slugFromParent,
  storefrontErrorMessage,
} from "@features/storefront/constants/storefront.constants";
import { StorefrontNotFoundComponent } from "../storefront-not-found/storefront-not-found.component";

type LoadResult =
  | { product: StorefrontProductDetail }
  | { notFound: string }
  | { error: string };

const PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm";

function parseProductId(raw: string | null): number | null {
  if (!raw || !/^[1-9]\d*$/.test(raw)) {
    return null;
  }
  const id = Number(raw);
  return Number.isSafeInteger(id) ? id : null;
}

@Component({
  selector: "app-storefront-product",
  standalone: true,
  imports: [RouterLink, StorefrontNotFoundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./storefront-product.component.html",
  styleUrls: ["./storefront-product.component.scss"],
})
export class StorefrontProductComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly storefront = inject(StorefrontService);
  private readonly title = inject(Title);
  private readonly destroyRef = inject(DestroyRef);

  private readonly reload$ = new Subject<void>();

  readonly slug = signal("");
  readonly loading = signal(true);
  readonly product = signal<StorefrontProductDetail | null>(null);
  readonly notFoundMessage = signal<string | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly selectedSkuId = signal<number | null>(null);
  readonly activeImageUrl = signal<string | null>(null);
  private readonly brokenUrls = signal<ReadonlySet<string>>(new Set());

  /** Sản phẩm dữ liệu cũ không có ảnh tải lên nhưng vẫn có thể có `imageUrl` ngoài. */
  readonly gallery = computed(() => {
    const product = this.product();
    if (!product) {
      return [];
    }
    const urls = product.images.map((image) => image.url);
    const all = urls.length === 0 && product.imageUrl ? [product.imageUrl] : urls;
    return all.filter((url) => !this.brokenUrls().has(url));
  });

  readonly shownImageUrl = computed(() => {
    const active = this.activeImageUrl();
    const gallery = this.gallery();
    return active && gallery.includes(active) ? active : (gallery[0] ?? null);
  });

  readonly selectedSku = computed<StorefrontSku | null>(
    () => this.product()?.skus.find((sku) => sku.id === this.selectedSkuId()) ?? null,
  );

  /** Một SKU không có tên biến thể là sản phẩm thường: không có gì để khách chọn. */
  readonly showVariants = computed(() => {
    const skus = this.product()?.skus ?? [];
    return skus.length > 1 || !!skus[0]?.variant;
  });

  readonly priceText = computed(() => {
    const product = this.product();
    if (!product) {
      return "";
    }
    const sku = this.selectedSku();
    return sku
      ? formatStorefrontPrice(sku.price)
      : formatStorefrontPriceRange(product.minPrice, product.maxPrice);
  });

  readonly inStock = computed(() => this.selectedSku()?.inStock ?? this.product()?.inStock ?? false);

  readonly description = computed(() => this.product()?.description?.trim() ?? "");

  constructor() {
    combineLatest([
      slugFromParent(this.route).pipe(tap((slug) => this.slug.set(slug))),
      this.route.paramMap.pipe(map((params) => parseProductId(params.get("id")))),
      this.reload$.pipe(startWith(undefined)),
    ])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.loadError.set(null);
          this.notFoundMessage.set(null);
        }),
        switchMap(([slug, id]) => this.load(slug, id)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => this.applyResult(result));
  }

  retry(): void {
    this.reload$.next();
  }

  selectImage(url: string): void {
    this.activeImageUrl.set(url);
  }

  selectSku(id: number): void {
    this.selectedSkuId.set(id);
  }

  onImageError(url: string | null): void {
    if (url) {
      this.brokenUrls.update((urls) => new Set(urls).add(url));
    }
  }

  private load(slug: string, id: number | null): Observable<LoadResult> {
    if (id === null) {
      return of({ notFound: PRODUCT_NOT_FOUND });
    }
    return this.storefront.getProduct(slug, id).pipe(
      map((product) => ({ product })),
      catchError((err: unknown) =>
        of<LoadResult>(
          isNotFoundError(err)
            ? { notFound: storefrontErrorMessage(err, PRODUCT_NOT_FOUND) }
            : { error: storefrontErrorMessage(err, "Không tải được sản phẩm, vui lòng thử lại.") },
        ),
      ),
    );
  }

  private applyResult(result: LoadResult): void {
    this.loading.set(false);
    if ("product" in result) {
      this.showProduct(result.product);
      return;
    }
    this.product.set(null);
    if ("notFound" in result) {
      this.notFoundMessage.set(result.notFound);
    } else {
      this.loadError.set(result.error);
    }
  }

  private showProduct(product: StorefrontProductDetail): void {
    const primary = product.images.find((image) => image.primary);
    this.product.set(product);
    this.activeImageUrl.set(primary?.url ?? null);
    this.selectedSkuId.set(product.skus.length === 1 ? product.skus[0].id : null);

    const shopName = this.storefront.currentShop()?.name;
    this.title.setTitle(shopName ? `${product.name} | ${shopName}` : product.name);
    // Từ cuối danh sách sang chi tiết: router của dự án không tự cuộn về đầu.
    window.scrollTo({ top: 0 });
  }
}
