#!/bin/bash
set -e

# Logging output to /var/log/user-data.log
exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1

echo "=========================================="
echo "Starting Perimity EC2 Provisioning Script"
echo "=========================================="

# 1. Update system packages
yum update -y

# 2. Install Docker & Git & AWS CLI
yum install -y docker git aws-cli

# 3. Start & Enable Docker
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user

# 4. Install Docker Compose v2 plugin
DOCKER_CONFIG=${DOCKER_CONFIG:-/usr/local/lib/docker}
mkdir -p $DOCKER_CONFIG/cli-plugins
curl -SL https://github.com/docker/compose/releases/download/v2.24.5/docker-compose-linux-x86_64 -o $DOCKER_CONFIG/cli-plugins/docker-compose
chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
ln -sf $DOCKER_CONFIG/cli-plugins/docker-compose /usr/bin/docker-compose

# 5. Create 4 GB Swap file to ensure t3.small (2GB RAM) never OOMs
if [ ! -f /swapfile ]; then
    echo "Creating 4 GB Swap space..."
    dd if=/dev/zero of=/swapfile bs=1M count=4096
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
    echo "Swap space enabled successfully."
fi

# 6. Create application directory
mkdir -p /opt/perimity
cd /opt/perimity

# 7. Login to AWS ECR using Instance IAM Role
AWS_REGION=$(curl -s http://169.254.169.254/latest/meta-data/placement/region || echo "us-east-1")
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text || echo "")

if [ -n "$AWS_ACCOUNT_ID" ]; then
    echo "Logging into AWS ECR ($AWS_ACCOUNT_ID in $AWS_REGION)..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
fi

# 8. Start production Docker Compose stack
if [ -f /opt/perimity/docker-compose.yml ]; then
    echo "Launching Perimity services..."
    docker-compose -f /opt/perimity/docker-compose.yml up -d --remove-orphans
fi

echo "=========================================="
echo "Perimity Provisioning Completed Successfully!"
echo "=========================================="
