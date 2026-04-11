import { Injectable, signal, computed } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { tap } from "rxjs/operators";
import { Observable, throwError } from "rxjs";
import { finalize, shareReplay } from "rxjs/operators";
import { ApiResponse } from "@core/models/api.model";
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserInfo,
} from "@core/models/auth.model";

const ACCESS_TOKEN_KEY = "access_token";
const REFRESH_TOKEN_KEY = "refresh_token";
const USER_KEY = "current_user";

@Injectable({ providedIn: "root" })
export class AuthService {
  private _currentUser = signal<UserInfo | null>(this.loadUser());
  private refreshInFlight$: Observable<ApiResponse<AuthResponse>> | null = null;

  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this._currentUser() !== null);
  readonly isOwner = computed(() => this._currentUser()?.role === "OWNER");

  constructor(
    private http: HttpClient,
    private router: Router,
  ) {}

  login(req: LoginRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http
      .post<ApiResponse<AuthResponse>>("/auth/login", req)
      .pipe(tap((res) => this.saveSession(res.data)));
  }

  register(req: RegisterRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http
      .post<ApiResponse<AuthResponse>>("/auth/register", req)
      .pipe(tap((res) => this.saveSession(res.data)));
  }

  refresh(): Observable<ApiResponse<AuthResponse>> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error("Missing refresh token"));
    }

    this.refreshInFlight$ = this.http
      .post<ApiResponse<AuthResponse>>("/auth/refresh", { refreshToken })
      .pipe(
        tap((res) => this.saveSession(res.data)),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay(1),
      );

    return this.refreshInFlight$;
  }

  logout(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      this.http.post("/auth/logout", { refreshToken }).subscribe();
    }
    this.clearSession();
    this.router.navigate(["/auth/login"]);
  }

  getAccessToken(): string | null {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  }

  private saveSession(auth: AuthResponse): void {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, auth.accessToken);
    sessionStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);
    sessionStorage.setItem(USER_KEY, JSON.stringify(auth.user));
    this._currentUser.set(auth.user);
  }

  private clearSession(): void {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    this._currentUser.set(null);
  }

  private loadUser(): UserInfo | null {
    const raw = sessionStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}
