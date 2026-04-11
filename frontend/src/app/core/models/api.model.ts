export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
}

export interface PageResponse<T> {
  content: T[];
  meta: PageMeta;
}

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageParams {
  page?: number;
  size?: number;
  sort?: string;
}
