import {
  HttpInterceptorFn,
  HttpRequest,
  HttpHandlerFn,
  HttpErrorResponse,
} from "@angular/common/http";
import { inject } from "@angular/core";
import { catchError, switchMap, throwError } from "rxjs";
import { AuthService } from "@core/auth/auth.service";

/** Máy chủ hoặc mạng lỗi tạm thời: refresh token có thể vẫn còn hạn nên không được xoá phiên. */
function isTransientFailure(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    (error.status === 0 || error.status >= 500)
  );
}

export const jwtInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
) => {
  const auth = inject(AuthService);

  const token = auth.getAccessToken();
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // 401 của /auth/* là lỗi nghiệp vụ, ví dụ sai mật khẩu; 403 là thiếu quyền thật, refresh không giải quyết được.
      if (error.status !== 401 || req.url.includes("/auth/")) {
        return throwError(() => error);
      }

      // catchError phải đứng trước switchMap: chỉ bắt lỗi của bước refresh.
      // Lỗi của request retry (ví dụ PUT trả 409) đi thẳng về component, không làm mất phiên.
      return auth.refresh().pipe(
        catchError((refreshError: unknown) => {
          if (!isTransientFailure(refreshError)) {
            auth.logout();
          }
          return throwError(() => error);
        }),
        switchMap(() => {
          const retried = req.clone({
            setHeaders: { Authorization: `Bearer ${auth.getAccessToken()}` },
          });
          return next(retried);
        }),
      );
    }),
  );
};
