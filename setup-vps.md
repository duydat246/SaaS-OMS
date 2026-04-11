Here's a practical guide to setting up a VPS to host and access your SaaS product from anywhere:

1. Choose a VPS Provider

DigitalOcean — easiest for beginners (Droplets)
Linode / Akamai — good value
Vultr — flexible pricing
AWS EC2 / Google Cloud / Azure — enterprise scale

2. Initial VPS Setup

# After SSH into your VPS (usually Ubuntu)

ssh root@YOUR_VPS_IP

# Update system

apt update && apt upgrade -y

# Create a non-root user

adduser yourname
usermod -aG sudo yourname

# Set up SSH key auth (from your local machine)

ssh-copy-id yourname@YOUR_VPS_IP 3. Install Required Software

# Node.js (via nvm)

curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
nvm install --lts

# Or Python

apt install python3 python3-pip -y

# Docker (recommended for isolation)

curl -fsSL https://get.docker.com | sh 4. Deploy Your Code
Option A — Git Pull (simplest)

# On VPS

git clone https://github.com/youruser/yourrepo.git /var/www/myapp
cd /var/www/myapp
npm install && npm run build
Option B — Docker Compose (recommended)

# docker-compose.yml on VPS

docker compose up -d 5. Set Up a Reverse Proxy (Nginx)

apt install nginx -y

# /etc/nginx/sites-available/myapp

server {
listen 80;
server_name yourdomain.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

}

ln -s /etc/nginx/sites-available/myapp /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx 6. Free HTTPS with Let's Encrypt

apt install certbot python3-certbot-nginx -y
certbot --nginx -d yourdomain.com 7. Keep Your App Running (PM2)

npm install -g pm2
pm2 start npm --name "myapp" -- start
pm2 startup # auto-start on reboot
pm2 save 8. Access From Anywhere
Method Use Case
Domain name Point DNS A record to VPS IP
SSH tunnel Secure access to internal services
VPN (WireGuard) Private team access
Cloudflare Tunnel No open ports needed 9. CI/CD for Automatic Deploys (optional)

# GitHub Actions: .github/workflows/deploy.yml

- name: Deploy to VPS
  run: |
  ssh user@VPS_IP "cd /var/www/myapp && git pull && npm install && pm2 restart myapp"

Quick Checklist
VPS created, SSH access working
Firewall configured (ufw allow 22,80,443)
App running via PM2 or Docker
Nginx reverse proxy set up
Domain DNS pointed to VPS IP
HTTPS certificate installed
Auto-deploy pipeline set up
