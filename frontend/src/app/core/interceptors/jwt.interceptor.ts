import {
  HttpInterceptorFn,
  HttpRequest,
  HttpHandlerFn,
  HttpErrorResponse,
} from "@angular/common/http";
import { inject } from "@angular/core";
import { catchError, switchMap, throwError } from "rxjs";
import { AuthService } from "@core/auth/auth.service";
import { Router } from "@angular/router";

export const jwtInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.getAccessToken();
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Lỗi 401/403 tạm thời được nuốt ở đây: refresh + retry; subscriber chỉ nhận kết quả sau cùng
      // (thành công hoặc lỗi thật) — UI không nên toast lỗi cho lần gọi API “hết token” trước khi retry.
      /*
       * 401: chuẩn “chưa / không còn xác thực”.
       * 403: Spring Security với JWT custom thường trả 403 khi JWT hết hạn/sai vì user vẫn là anonymous
       *       nhưng endpoint yêu cầu authenticated() — không phải lúc nào cũng là “đủ quyền nhưng bị cấm”.
       */
      const tryRefresh =
        (error.status === 401 || error.status === 403) &&
        !req.url.includes("/auth/");

      if (tryRefresh) {
        return auth.refresh().pipe(
          switchMap(() => {
            const newToken = auth.getAccessToken();
            const retried = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` },
            });
            return next(retried);
          }),
          catchError(() => {
            auth.logout();
            return throwError(() => error);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
