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
DOCKERHUB_USERNAME=your_dockerhub_username
APP_CORS_ALLOWED_ORIGINS=https://yourdomain.com
```

Tạo JWT_SECRET ngẫu nhiên:
```bash
openssl rand -hex 32
```

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
saasoms-frontend-1  Up  0.0.0.0:80->80/tcp
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
# 1. Build Docker image (~5 phút)
# 2. Push lên Docker Hub
# 3. SSH vào VPS → pull image mới → restart
```

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
```bash
cd /var/www/saasoms
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --force-recreate
```

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
