import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";

interface HeartParticle {
  x: number;
  y: number;
  dx: number;
  dy: number;
  life: number;
  h: number;
  s: number;
  l: number;
}

/** Điểm trên đường cong trái tim (tham số t ∈ [0, 2π]). Trục y hướng lên trong toán học. */
function heartOutline(t: number): { hx: number; hy: number } {
  const hx = 16 * Math.pow(Math.sin(t), 3);
  const hy = -(
    13 * Math.cos(t) -
    5 * Math.cos(2 * t) -
    2 * Math.cos(3 * t) -
    Math.cos(4 * t)
  );
  return { hx, hy };
}

function randomPinkParts(): { h: number; s: number; l: number } {
  return {
    h: 320 + Math.random() * 38,
    s: 72 + Math.random() * 22,
    l: 62 + Math.random() * 22,
  };
}

@Component({
  selector: "app-product-list",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./product-list.component.html",
  styleUrls: ["./product-list.component.scss"],
})
export class ProductListComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild("viewport", { static: false })
  viewportRef!: ElementRef<HTMLElement>;

  @ViewChild("canvas", { static: false })
  canvasRef!: ElementRef<HTMLCanvasElement>;

  private ctx!: CanvasRenderingContext2D;
  private fireworks: HeartParticle[][] = [];
  private resizeObserver?: ResizeObserver;

  name = "vợ yêu";
  message = "luôn xinh đẹp và yêu anh";

  ngOnInit(): void {
    this.startShow();
  }

  ngAfterViewInit(): void {
    const canvas = this.canvasRef.nativeElement;
    this.ctx = canvas.getContext("2d")!;

    this.resizeObserver = new ResizeObserver(() => this.syncCanvasSize());
    this.resizeObserver.observe(this.viewportRef.nativeElement);

    this.syncCanvasSize();
    this.animate();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  @HostListener("window:resize")
  onWindowResize(): void {
    this.syncCanvasSize();
  }

  /** Kích thước buffer = khung nội dung (không phủ sidebar). */
  private syncCanvasSize(): void {
    const canvas = this.canvasRef?.nativeElement;
    const vp = this.viewportRef?.nativeElement;
    if (!canvas || !vp) return;

    const w = Math.max(1, Math.round(vp.clientWidth));
    const h = Math.max(1, Math.round(vp.clientHeight));

    if (canvas.width === w && canvas.height === h) return;

    canvas.width = w;
    canvas.height = h;
  }

  private get canvasW(): number {
    return this.canvasRef?.nativeElement?.width ?? 0;
  }

  private get canvasH(): number {
    return this.canvasRef?.nativeElement?.height ?? 0;
  }

  createFirework(): void {
    if (!this.canvasRef?.nativeElement) return;

    const w = this.canvasW;
    const h = this.canvasH;

    const cx = w * (0.38 + Math.random() * 0.24);
    const cy = h * (0.22 + Math.random() * 0.28);
    const scale = Math.min(w, h) / 52;

    const particleCount = 96;
    const particles: HeartParticle[] = [];

    for (let i = 0; i < particleCount; i++) {
      const t = (Math.PI * 2 * i) / particleCount + (Math.random() - 0.5) * 0.08;
      const { hx, hy } = heartOutline(t);
      const mag = Math.hypot(hx, hy) + 1e-4;
      const speed = (2.2 + Math.random() * 2.8) * scale * 0.18;
      const jitter = 0.88 + Math.random() * 0.24;

      const { h, s, l } = randomPinkParts();
      particles.push({
        x: cx,
        y: cy,
        dx: (hx / mag) * speed * jitter,
        dy: (hy / mag) * speed * jitter,
        life: 85 + Math.floor(Math.random() * 45),
        h,
        s,
        l,
      });
    }

    this.fireworks.push(particles);
  }

  startShow(): void {
    setInterval(() => this.createFirework(), 500);
  }

  animate(): void {
    requestAnimationFrame(() => this.animate());

    const w = this.canvasW;
    const h = this.canvasH;
    if (!this.ctx || w === 0 || h === 0) return;

    this.ctx.fillStyle = "rgba(0,0,0,0.18)";
    this.ctx.fillRect(0, 0, w, h);

    this.fireworks.forEach((particles, index) => {
      particles.forEach((p) => {
        p.x += p.dx;
        p.y += p.dy;
        p.dy += 0.035;
        p.life--;

        const alpha = Math.min(1, p.life / 40);
        this.ctx.beginPath();
        this.ctx.arc(p.x, p.y, 2.2, 0, Math.PI * 2);
        this.ctx.fillStyle = `hsla(${p.h.toFixed(0)}, ${p.s.toFixed(0)}%, ${p.l.toFixed(0)}%, ${alpha.toFixed(3)})`;
        this.ctx.fill();
      });

      this.fireworks[index] = particles.filter((p) => p.life > 0);
    });

    this.drawText();
  }

  drawText(): void {
    if (!this.name && !this.message) return;
    if (!this.ctx) return;

    const w = this.canvasW;
    const h = this.canvasH;
    const titlePx = Math.max(14, Math.min(36, w * 0.065));
    const msgPx = Math.max(12, Math.min(22, w * 0.038));

    this.ctx.fillStyle = "white";
    this.ctx.textAlign = "center";

    this.ctx.font = `${Math.round(titlePx)}px Arial, sans-serif`;
    this.ctx.fillText(`Chúc ${this.name}`, w / 2, h / 2);

    this.ctx.font = `${Math.round(msgPx)}px Arial, sans-serif`;
    this.ctx.fillText(this.message, w / 2, h / 2 + titlePx * 0.9);
  }
}
