# Hướng dẫn triển khai SaaS OMS lên VPS

## Yêu cầu
- VPS Ubuntu 24.04 (tối thiểu 1 Core, 1GB RAM)
- Domain đã trỏ về Cloudflare
- Tài khoản Docker Hub
- Tài khoản GitHub

---

## Phần 1: Chuẩn bị tài khoản Docker Hub

1. Đăng ký tại [hub.docker.com](https://hub.docker.com)
2. Vào **Account Settings → Security → Personal Access Tokens → Generate new token**
   - Description: `github-actions`
   - Permissions: `Read & Write`
   - Copy token (chỉ hiện 1 lần)

---

## Phần 2: Thêm Secrets vào GitHub repo

Vào **GitHub repo → Settings → Secrets and variables → Actions → New repository secret**

| Secret | Giá trị |
|---|---|
| `DOCKERHUB_USERNAME` | username Docker Hub |
| `DOCKERHUB_TOKEN` | token vừa tạo |
| `VPS_HOST` | IP VPS |
| `VPS_USER` | `root` |
| `VPS_SSH_KEY` | private key SSH (xem Phần 3) |

---

## Phần 3: Tạo SSH Key (chạy trên VPS)

```bash
ssh-keygen -t rsa -b 4096 -C "deploy-key"
# Nhấn Enter 3 lần (để trống hết)

# Thêm public key vào authorized_keys
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

# Copy private key → dán vào GitHub Secret VPS_SSH_KEY
cat ~/.ssh/id_rsa
```

---

## Phần 4: Setup VPS

```bash
# 1. Cài Docker
curl -fsSL https://get.docker.com | sh

# 2. Thêm Swap 2GB (bắt buộc với RAM 1GB)
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 3. Tạo thư mục deploy
mkdir -p /var/www/saasoms
cd /var/www/saasoms

# 4. Tải file docker-compose
curl -o docker-compose.prod.yml \
  https://raw.githubusercontent.com/duydat246/SaaS-OMS/dev/docker-compose.prod.yml

# 5. Tạo file .env
nano .env
```

Nội dung file `.env`:
```env
DB_USERNAME=saasoms_user
DB_PASSWORD=your_strong_password
MYSQL_ROOT_PASSWORD=your_root_password
JWT_SECRET=your_jwt_secret_32_chars_minimum
APP_MEDIA_SECRET=your_media_secret_32_chars_minimum
DOCKERHUB_USERNAME=your_dockerhub_username
APP_CORS_ALLOWED_ORIGINS=https://yourdomain.com
```

`APP_CORS_ALLOWED_ORIGINS` phải khớp chính xác địa chỉ người dùng mở app: đúng `https`, đúng tên miền, không có `/` ở cuối, nhiều giá trị thì ngăn cách bằng dấu phẩy và không có dấu cách. Sai giá trị thì mọi request có `Origin`, gồm cả đăng nhập, bị 403 `Invalid CORS request`.

`JWT_SECRET` và `APP_MEDIA_SECRET` đều bắt buộc: thiếu một trong hai thì `docker compose` báo lỗi và không chạy. Không đặt `IMAGE_TAG` trong `.env` khi triển khai bình thường, xem [Rollback theo commit](#rollback-theo-commit).

Tạo `JWT_SECRET` và `APP_MEDIA_SECRET` ngẫu nhiên, mỗi biến một giá trị riêng:
```bash
openssl rand -hex 32
```

`docker-compose.prod.yml` chỉ mở cổng 80 trên `127.0.0.1`: từ Internet không vào thẳng được, mọi lượt truy cập đi qua Cloudflare Tunnel ở Phần 5.

---

## Phần 5: Cài Cloudflare Tunnel

```bash
# Cài cloudflared
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb \
  -o cloudflared.deb
dpkg -i cloudflared.deb

# Đăng nhập Cloudflare
cloudflared tunnel login

# Tạo tunnel
cloudflared tunnel create ten-tunnel

# Cấu hình tunnel
nano ~/.cloudflared/config.yml
```

Nội dung `config.yml`:
```yaml
tunnel: ten-tunnel
credentials-file: /root/.cloudflared/<tunnel-id>.json

ingress:
  - hostname: app.yourdomain.com
    service: http://localhost:80
  - service: http_status:404
```

```bash
# Cài như service tự khởi động
cloudflared service install
systemctl enable cloudflared
systemctl start cloudflared

# Kiểm tra
systemctl status cloudflared
```

---

## Phần 6: Push code lên GitHub → Trigger CI/CD

```bash
# Trên máy local
git add .
git commit -m "initial deploy"
git push origin dev
```

Theo dõi tại **GitHub repo → tab Actions** — chờ ✅ xanh (~5-10 phút).

---

## Phần 7: Kiểm tra trên VPS

```bash
cd /var/www/saasoms
docker compose -f docker-compose.prod.yml ps
```

Kết quả mong đợi:
```
saasoms-mysql-1     Up (healthy)
saasoms-backend-1   Up
saasoms-frontend-1  Up  127.0.0.1:80->80/tcp
```

Truy cập domain → App hoạt động.

---

## Quy trình deploy hàng ngày

```bash
# Sửa code xong, chỉ cần 3 lệnh:
git add .
git commit -m "mô tả thay đổi"
git push origin dev

# GitHub Actions tự động:
# 1. Chạy test backend và build frontend; đỏ thì dừng, không deploy
# 2. Build Docker image, push lên Docker Hub với tag latest và commit SHA
# 3. SSH vào VPS → pull image theo SHA → restart
# 4. Chờ backend sẵn sàng tối đa 6 phút; không đạt thì in log và báo job đỏ
```

Job đỏ ở bước 4 không tự rollback: phiên bản mới vẫn đang chạy, cần xử lý theo [Rollback theo commit](#rollback-theo-commit).

CI không đồng bộ `docker-compose.prod.yml` lên VPS. Khi file này thay đổi, phải cập nhật bản trên VPS trước khi push:
```bash
cd /var/www/saasoms
cp docker-compose.prod.yml docker-compose.prod.yml.bak
curl -o docker-compose.prod.yml \
  https://raw.githubusercontent.com/duydat246/SaaS-OMS/dev/docker-compose.prod.yml
```
Cách tải trên lấy bản đã có trên nhánh `dev`; nếu thay đổi chưa push thì chép file bằng `scp`.

---

## Xử lý sự cố thường gặp

### App không truy cập được (Error 1033)
```bash
# Cloudflared bị dừng
systemctl start cloudflared
systemctl status cloudflared
```

### Xem logs
```bash
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend
docker compose -f docker-compose.prod.yml logs -f mysql
```

### Restart thủ công

`IMAGE_TAG` chỉ tồn tại trong phiên deploy của CI, không được lưu lại trên VPS. Phải tự đặt trước khi chạy `pull` hoặc `up`. Nếu không, compose dùng `latest`, tức bản build mới nhất, kể cả bản vừa deploy lỗi hoặc bản đã rollback khỏi.

```bash
cd /var/www/saasoms
# Giữ đúng phiên bản đang chạy
export IMAGE_TAG=$(docker inspect --format '{{.Config.Image}}' saasoms-backend-1 | cut -d: -f2)
echo "$IMAGE_TAG"
docker compose -f docker-compose.prod.yml up -d --force-recreate
```

Khởi động lại VPS thì không cần bước này: `restart: always` chạy lại đúng container đang có.

### Rollback theo commit

Mỗi lần CI chạy, image được đẩy lên Docker Hub với tag là commit SHA đầy đủ. Lấy SHA ở tab Actions, `git log` hoặc trang tag trên Docker Hub.

```bash
cd /var/www/saasoms
export IMAGE_TAG=<sha-đầy-đủ-của-commit>
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

- Kiểm tra backend đã sẵn sàng: lệnh sau trả `400` là đạt.
  ```bash
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{}' http://127.0.0.1/auth/refresh
  ```
- Chỉ rollback được về commit đã build sau khi CI có tag SHA; bản cũ hơn chỉ có `latest`.
- Rollback không đảo ngược migration. Nếu commit đích cũ hơn một migration đã chạy, kiểm tra schema hiện tại còn tương thích với code cũ trước khi rollback.
- Lần push `dev` tiếp theo sẽ deploy đè lên bản đã rollback. Nếu lỗi chưa được sửa, `git revert` commit lỗi rồi push.
- Muốn bản rollback giữ nguyên qua các lần restart thủ công, có thể ghi `IMAGE_TAG=<sha>` vào `.env`, và xoá dòng này khi đã xử lý xong. Biến `export` trong shell, gồm cả của CI, được ưu tiên hơn `.env`.

### Rate-limit đăng nhập và IP thật

Sau khi deploy trên VPS mới hoặc khi tạo lại mạng Docker, phải kiểm tra backend nhận đúng IP thật của người dùng. Rate-limit đếm theo IP mà nginx chuyển cho backend, và nginx chỉ tin `CF-Connecting-IP` khi kết nối đến từ dải `set_real_ip_from 172.16.0.0/12` trong `frontend/nginx.conf`. Dải này phải chứa gateway của mạng Docker, vì cloudflared chạy trên host và đi vào container qua gateway đó.

1. Xem gateway của mạng. Tên mạng là `<thư mục deploy>_saasoms-net`; nếu không chắc thì tìm bằng `docker network ls`.
   ```bash
   docker network inspect saasoms_saasoms-net --format '{{json .IPAM.Config}}'
   ```
   `Gateway` phải nằm trong `172.16.0.0` đến `172.31.255.255`.
2. Mở app qua tên miền từ máy của bạn, rồi xem cột đầu tiên của log nginx.
   ```bash
   docker compose -f docker-compose.prod.yml logs --tail=20 frontend
   ```
   Phải thấy IP công khai của máy bạn, không phải IP gateway. Dòng do curl từ `127.0.0.1` trên VPS, gồm cả healthcheck của CI, vẫn hiện gateway vì không đi qua Cloudflare, đây là bình thường.

Nếu lệch, mọi người dùng bị tính chung một IP: 30 lần đăng nhập sai trên toàn hệ thống sẽ chặn đăng nhập của tất cả mọi người trong 15 phút, và 10 lần đăng ký trong 1 giờ sẽ chặn đăng ký. Cách xử lý: sửa `set_real_ip_from` thành dải chứa gateway rồi deploy lại, vì `nginx.conf` được đóng trong image frontend.

Các lệnh và kết quả mong đợi ở mục này chưa được chạy thử trên VPS thật.

### Kiểm tra RAM
```bash
free -h
docker stats --no-stream
```

---

## Phân bổ tài nguyên (VPS 1GB RAM)

| Service | RAM tối đa |
|---|---|
| OS + Docker | ~200MB |
| MySQL | 256MB |
| Backend (JVM) | 320MB |
| Nginx Frontend | 32MB |
| Swap buffer | 2GB |
