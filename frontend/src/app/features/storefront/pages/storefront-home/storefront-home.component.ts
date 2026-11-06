import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, ParamMap, Params, Router, RouterLink } from "@angular/router";
import { catchError, combineLatest, debounceTime, map, of, startWith, Subject, switchMap, tap } from "rxjs";
import { PageMeta } from "@core/models/api.model";
import { StorefrontProductSummary } from "@features/storefront/models/storefront.model";
import { StorefrontService } from "@features/storefront/services/storefront.service";
import {
  formatStorefrontPriceFrom,
  slugFromParent,
  STOREFRONT_DEFAULT_SORT,
  STOREFRONT_PAGE_SIZE,
  STOREFRONT_SEARCH_MAX_LENGTH,
  STOREFRONT_SORT_OPTIONS,
  storefrontErrorMessage,
  telLink,
} from "@features/storefront/constants/storefront.constants";

const SEARCH_DEBOUNCE_MS = 350;
const INT_MAX = 2147483647;
/** Thẻ sản phẩm xuất hiện so le, nhưng không để thẻ cuối trang chờ quá lâu. */
const TILE_STAGGER_MS = 40;
const TILE_STAGGER_MAX = 12;
const COUNT_FORMAT = new Intl.NumberFormat("vi-VN");

interface HeroStat {
  key: string;
  icon: string;
  label: string;
  value: string;
  href: string | null;
  /** Ô chiếm cả hàng trên lưới hai cột của điện thoại, để giá trị dài không bị cắt. */
  wide: boolean;
}

interface ListQuery {
  q: string;
  category: string;
  sort: string;
  page: number;
}

function parseListQuery(params: ParamMap): ListQuery {
  const sort = params.get("sort");
  const page = Number(params.get("page"));
  return {
    q: (params.get("q") ?? "").trim(),
    category: (params.get("category") ?? "").trim(),
    sort: STOREFRONT_SORT_OPTIONS.find((o) => o.value === sort)?.value ?? STOREFRONT_DEFAULT_SORT,
    // Giá trị sai trên URL về trang đầu, không để backend trả 400.
    page: Number.isInteger(page) && page > 0 && page <= INT_MAX ? page : 0,
  };
}

@Component({
  selector: "app-storefront-home",
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./storefront-home.component.html",
  styleUrls: ["../../styles/storefront-base.scss", "./storefront-home.component.scss"],
})
export class StorefrontHomeComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly storefront = inject(StorefrontService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly reload$ = new Subject<void>();
  private readonly reloadCategories$ = new Subject<void>();
  private readonly searchInput$ = new Subject<void>();
  /** Từ khoá đã đưa lên URL: URL cũ về muộn không được ghi đè chữ khách đang gõ. */
  private syncedSearch: string | null = null;

  readonly slug = signal("");
  readonly products = signal<StorefrontProductSummary[]>([]);
  readonly meta = signal<PageMeta | null>(null);
  /** `meta` hiện tại là kết quả có lọc hay toàn bộ sản phẩm, để nhãn số liệu khớp con số. */
  private readonly metaFiltered = signal(false);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly categories = signal<string[]>([]);
  readonly categoriesError = signal<string | null>(null);
  readonly query = signal<ListQuery>(parseListQuery(this.route.snapshot.queryParamMap));
  readonly searchText = signal("");
  private readonly brokenImageIds = signal<ReadonlySet<number>>(new Set());

  readonly sortOptions = STOREFRONT_SORT_OPTIONS;
  readonly searchMaxLength = STOREFRONT_SEARCH_MAX_LENGTH;
  readonly skeletonTiles = [1, 2, 3, 4, 5, 6, 7, 8];
  readonly priceLabel = (product: StorefrontProductSummary) =>
    formatStorefrontPriceFrom(product.minPrice, product.maxPrice);

  readonly shop = this.storefront.currentShop;
  readonly tileDelay = (index: number) => `${Math.min(index, TILE_STAGGER_MAX) * TILE_STAGGER_MS}ms`;

  /** Dải thông số trên hero: chỉ dữ liệu thật đang có, thiếu thì ẩn cột. */
  readonly heroStats = computed<HeroStat[]>(() => {
    const stats: HeroStat[] = [];
    const meta = this.meta();
    if (meta) {
      stats.push({
        key: "products",
        icon: "pi-box",
        label: this.metaFiltered() ? "Kết quả lọc" : "Sản phẩm",
        value: COUNT_FORMAT.format(meta.totalElements),
        href: null,
        wide: false,
      });
    }
    const categoryCount = this.categories().length;
    if (categoryCount > 0) {
      stats.push({
        key: "categories",
        icon: "pi-tags",
        label: "Danh mục",
        value: COUNT_FORMAT.format(categoryCount),
        href: null,
        wide: false,
      });
    }
    const phone = this.shop()?.contactPhone;
    if (phone) {
      stats.push({ key: "contact", icon: "pi-phone", label: "Liên hệ", value: phone, href: telLink(phone), wide: true });
    }
    return stats;
  });

  readonly hasFilters = computed(() => !!this.query().q || !!this.query().category);
  /** Danh mục trên URL có thể đã hết sản phẩm: vẫn hiện để khách thấy đang lọc gì và bỏ lọc được. */
  readonly categoryChips = computed(() => {
    const selected = this.query().category;
    const list = this.categories();
    return selected && !list.includes(selected) ? [selected, ...list] : list;
  });

  constructor() {
    const shop = this.storefront.currentShop();
    if (shop) {
      inject(Title).setTitle(shop.name);
    }

    const slug$ = slugFromParent(this.route).pipe(tap((slug) => this.slug.set(slug)));

    combineLatest([
      slug$,
      this.route.queryParamMap.pipe(map(parseListQuery)),
      this.reload$.pipe(startWith(undefined)),
    ])
      .pipe(
        tap(([, query]) => {
          this.applyQuery(query);
          this.loading.set(true);
          this.loadError.set(null);
        }),
        // switchMap: đổi bộ lọc nhanh thì response cũ không được ghi đè response mới.
        switchMap(([slug, query]) =>
          this.storefront
            .getProducts(slug, {
              search: query.q,
              category: query.category,
              sort: query.sort,
              page: query.page,
              size: STOREFRONT_PAGE_SIZE,
            })
            .pipe(
              map((page) => ({ page, error: null })),
              catchError((err: unknown) =>
                of({
                  page: null,
                  error: storefrontErrorMessage(err, "Không tải được danh sách sản phẩm, vui lòng thử lại."),
                }),
              ),
            ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ page, error }) => {
        if (!page) {
          this.loading.set(false);
          this.products.set([]);
          this.meta.set(null);
          this.loadError.set(error);
          return;
        }
        // Mở URL cũ khi số trang đã giảm: lùi về trang cuối còn dữ liệu.
        const lastPage = Math.max(page.meta.totalPages - 1, 0);
        if (page.content.length === 0 && page.meta.page > lastPage) {
          this.goTo({ page: lastPage }, true);
          return;
        }
        this.loading.set(false);
        this.products.set(page.content);
        this.meta.set(page.meta);
        this.metaFiltered.set(this.hasFilters());
      });

    combineLatest([slugFromParent(this.route), this.reloadCategories$.pipe(startWith(undefined))])
      .pipe(
        tap(() => this.categoriesError.set(null)),
        switchMap(([slug]) =>
          this.storefront.getCategories(slug).pipe(
            map((categories) => ({ categories, error: null })),
            catchError((err: unknown) =>
              of({
                categories: [] as string[],
                error: storefrontErrorMessage(err, "Không tải được danh mục."),
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ categories, error }) => {
        this.categories.set(categories);
        this.categoriesError.set(error);
      });

    this.searchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.submitSearch(true));
  }

  onSearchInput(value: string): void {
    this.searchText.set(value);
    this.searchInput$.next();
  }

  /** `replaceUrl` khi tự chạy lúc đang gõ, để nút Back không đi qua từng từ khoá dở. */
  submitSearch(replaceUrl = false): void {
    if (this.searchText().trim() !== this.query().q) {
      this.goTo({ page: 0 }, replaceUrl);
    }
  }

  selectCategory(category: string): void {
    this.goTo({ category, page: 0 });
  }

  onSortChange(sort: string): void {
    this.goTo({ sort, page: 0 });
  }

  goToPage(page: number): void {
    this.goTo({ page });
    window.scrollTo({ top: 0 });
  }

  clearFilters(): void {
    this.searchText.set("");
    this.goTo({ category: "", page: 0 });
  }

  retry(): void {
    this.reload$.next();
  }

  retryCategories(): void {
    this.reloadCategories$.next();
  }

  isImageBroken(id: number): boolean {
    return this.brokenImageIds().has(id);
  }

  onImageError(id: number): void {
    this.brokenImageIds.update((ids) => new Set(ids).add(id));
  }

  private applyQuery(query: ListQuery): void {
    this.query.set(query);
    if (query.q !== this.syncedSearch) {
      this.syncedSearch = query.q;
      this.searchText.set(query.q);
    }
  }

  private goTo(changes: Partial<ListQuery>, replaceUrl = false): void {
    const next: ListQuery = { ...this.query(), ...changes, q: this.searchText().trim() };
    this.syncedSearch = next.q;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.toQueryParams(next),
      replaceUrl,
    });
  }

  private toQueryParams(query: ListQuery): Params {
    const params: Params = {};
    if (query.q) params["q"] = query.q;
    if (query.category) params["category"] = query.category;
    if (query.sort !== STOREFRONT_DEFAULT_SORT) params["sort"] = query.sort;
    if (query.page > 0) params["page"] = String(query.page);
    return params;
  }
}
