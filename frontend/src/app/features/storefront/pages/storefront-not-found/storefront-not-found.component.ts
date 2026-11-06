import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, RouterLink } from "@angular/router";

@Component({
  selector: "app-storefront-not-found",
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./storefront-not-found.component.html",
  styleUrls: ["../../styles/storefront-base.scss", "./storefront-not-found.component.scss"],
})
export class StorefrontNotFoundComponent {
  private readonly title = inject(Title);

  /** Route `**` truyền qua `data`; component nhúng trong template truyền trực tiếp. */
  readonly heading = input<string>();
  readonly detail = input<string>();

  readonly headingText = computed(() => this.heading() ?? "Không tìm thấy trang");
  /** Chỉ có khi đang ở trong một shop đã mở được: đưa khách về danh sách của shop đó. */
  readonly shopSlug = inject(ActivatedRoute).snapshot.parent?.paramMap.get("slug") ?? null;

  constructor() {
    effect(() => this.title.setTitle(this.headingText()));
  }
}
