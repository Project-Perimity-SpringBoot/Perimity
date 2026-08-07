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
#
# -f MATTERS HERE. Without it, a 404 - which is what a PRIVATE repository
# returns to an unauthenticated request - is written to the file as an HTML
# error page and curl still exits 0. Step 9 then sees a file that exists, runs
# `docker-compose up` against HTML, and fails with a YAML parse error that says
# nothing about the repository being private.
#
# With -f the download fails loudly here instead, while the reason is still
# obvious.
curl -fsSL https://raw.githubusercontent.com/Project-Perimity-SpringBoot/Perimity/main/docker-compose.prod.yml \
    -o /opt/perimity/docker-compose.prod.yml

# 6b. Fetch the Postgres init script THE COMPOSE FILE BIND-MOUNTS
#
# ==========================================================================
# WITHOUT THIS, EVERY JAVA SERVICE CRASH-LOOPS AND NOTHING SAYS WHY
# ==========================================================================
# docker-compose.prod.yml mounts a HOST path into the Postgres container:
#
#     ./docker/postgres/init-databases.sql:/docker-entrypoint-initdb.d/...
#
# Only the compose file was downloaded, so that path did not exist - and
# Docker does not fail on a missing bind-mount source, it CREATES IT AS AN
# EMPTY DIRECTORY. Postgres then skipped it (initdb.d ignores directories),
# so authdb, userdb, gatepassdb, campusdb and qrdb were never created.
#
# Every service then died on startup with
#
#     HibernateException: Unable to determine Dialect without JDBC metadata
#
# which names the symptom and not the cause: the database it was told to
# connect to simply did not exist. Postgres itself was healthy throughout, so
# compose reported the stack as up while six containers restarted forever.
#
# The mount is relative to the compose file, so the directory layout has to be
# reproduced exactly - hence mkdir -p on the same two levels.
mkdir -p /opt/perimity/docker/postgres
curl -fsSL https://raw.githubusercontent.com/Project-Perimity-SpringBoot/Perimity/main/docker/postgres/init-databases.sql \
    -o /opt/perimity/docker/postgres/init-databases.sql

# 7. Login to AWS ECR using Instance IAM Role
#
# ==========================================================================
# IMDSv2: THE TOKEN IS NOT OPTIONAL ON AMAZON LINUX 2023
# ==========================================================================
# This was a plain unauthenticated curl, and it is why nothing ever started.
# AL2023 requires a session token for instance metadata, so the request comes
# back HTTP 401 with an EMPTY BODY - and, crucially, curl exits 0. A non-zero
# exit is what `|| echo "us-east-1"` waits for, so the fallback never fired and
# AWS_REGION was set to the empty string.
#
# The next line then ran `aws ecr get-login-password --region ` with nothing
# after the flag:
#
#     aws: [ERROR]: argument --region: expected one argument
#     Error: Cannot perform an interactive login from a non TTY device
#
# and `set -e` killed the script before the compose stack was ever started.
# Every instance then failed its ELB health check and the ASG replaced it, in a
# loop, roughly every six minutes.
#
# -f is on the token request so a genuine failure is a non-zero exit rather
# than a silent empty string - the exact trap this is fixing. The `:-` default
# on the next line is the real belt-and-braces: it triggers on empty, which is
# what actually happened, where `||` only triggers on failure.
TOKEN=$(curl -sf -X PUT "http://169.254.169.254/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" || echo "")

AWS_REGION=$(curl -sf -H "X-aws-ec2-metadata-token: $TOKEN" \
    http://169.254.169.254/latest/meta-data/placement/region || echo "")
AWS_REGION=${AWS_REGION:-us-east-1}

# The CLI negotiates its own IMDSv2 token, so this call works where the raw
# curl above did not. Defaulted the same way and for the same reason.
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --region "$AWS_REGION" \
    --query "Account" --output text || echo "")
AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID:-682975283868}

echo "Resolved region=$AWS_REGION account=$AWS_ACCOUNT_ID"

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
