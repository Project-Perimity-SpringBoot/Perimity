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

# 5. Create application directory
mkdir -p /opt/perimity
cd /opt/perimity

# 6. Fetch docker-compose.prod.yml from GitHub
curl -sSL https://raw.githubusercontent.com/Project-Perimity-SpringBoot/Perimity/main/docker-compose.prod.yml -o /opt/perimity/docker-compose.prod.yml

# 7. Login to AWS ECR using Instance IAM Role
AWS_REGION=$(curl -s http://169.254.169.254/latest/meta-data/placement/region || echo "us-east-1")
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text || echo "682975283868")

if [ -n "$AWS_ACCOUNT_ID" ]; then
    echo "Logging into AWS ECR ($AWS_ACCOUNT_ID in $AWS_REGION)..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
fi

# 8. Create default .env if not present
if [ ! -f /opt/perimity/.env ]; then
cat << 'EOF' > /opt/perimity/.env
POSTGRES_USER=perimity
POSTGRES_PASSWORD=perimity_prod_pass
JWT_SECRET=change_me_to_a_long_random_string_at_least_32_chars
INTERNAL_API_KEY=change_me_internal_key
QR_AES_KEY=4qX9L+vW8z+A7k3m9N2p5Q8r1T4v7X0z3B6e9H2k5M8=
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM_NAME=Perimity
EOF
fi

# 9. Start production Docker Compose stack
if [ -f /opt/perimity/docker-compose.prod.yml ]; then
    echo "Launching Perimity services..."
    AWS_ACCOUNT_ID=$AWS_ACCOUNT_ID AWS_REGION=$AWS_REGION docker-compose -f /opt/perimity/docker-compose.prod.yml up -d --remove-orphans
fi

echo "=========================================="
echo "Perimity Provisioning Completed Successfully!"
echo "=========================================="
