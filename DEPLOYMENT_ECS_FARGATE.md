# ECS Fargate Deployment Runbook

This runbook prepares the hotel booking backend for the required AWS Academy Learner Lab deployment:

- Amazon ECR for the Docker image
- Amazon ECS Cluster with AWS Fargate
- ECS Task Definition and ECS Service
- Application Load Balancer and Target Group
- Amazon RDS MySQL
- AWS Secrets Manager for database password and JWT secret
- CloudWatch Logs
- Health check: `GET /actuator/health`

The commands below are written for AWS CloudShell. Replace placeholder values before running.

## 1. Project Readiness

The project already includes:

- `Dockerfile`
- `.dockerignore`
- `spring-boot-starter-actuator`
- Public health endpoint: `/actuator/health`
- Environment-backed config:
  - `PORT`
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `JWT_SECRET`
  - `UPLOAD_DIR`
  - `SWAGGER_ENABLED`

Build locally before deployment:

```bash
mvn clean package
```

Expected jar:

```text
target/rest-0.0.1-SNAPSHOT.jar
```

## 2. Set CloudShell Variables

In AWS CloudShell:

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

export APP_NAME=hotel-booking-backend
export ECR_REPOSITORY=$APP_NAME
export IMAGE_TAG=latest

export VPC_ID=vpc-xxxxxxxx
export PUBLIC_SUBNET_1=subnet-xxxxxxxx
export PUBLIC_SUBNET_2=subnet-yyyyyyyy
export PRIVATE_SUBNET_1=subnet-aaaaaaaa
export PRIVATE_SUBNET_2=subnet-bbbbbbbb

export DB_NAME=hotel_booking
export DB_USERNAME=hotel_admin
export DB_PASSWORD='REPLACE_WITH_STRONG_PASSWORD'
export JWT_SECRET='REPLACE_WITH_BASE64_SECRET'
```

Generate a valid JWT secret:

```bash
openssl rand -base64 32
```

If your Learner Lab VPC does not have private subnets, use two public subnets for ECS and RDS for the course demo, but keep security groups restrictive.

## 3. Create ECR Repository

```bash
aws ecr create-repository \
  --repository-name "$ECR_REPOSITORY" \
  --image-scanning-configuration scanOnPush=true \
  --region "$AWS_REGION"
```

Authenticate Docker to ECR:

```bash
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login \
      --username AWS \
      --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
```

## 4. Build, Tag, And Push Docker Image

Run these from the project root:

```bash
docker build -t "$APP_NAME:$IMAGE_TAG" .

docker tag "$APP_NAME:$IMAGE_TAG" \
  "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"

docker push "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"
```

Image URI:

```bash
export IMAGE_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"
echo "$IMAGE_URI"
```

## 5. Create Security Groups

Application Load Balancer security group:

```bash
export ALB_SG_ID=$(aws ec2 create-security-group \
  --group-name hotel-alb-sg \
  --description "Hotel API ALB public HTTP access" \
  --vpc-id "$VPC_ID" \
  --query GroupId \
  --output text)

aws ec2 authorize-security-group-ingress \
  --group-id "$ALB_SG_ID" \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0
```

ECS task security group. It accepts app traffic only from the ALB security group:

```bash
export ECS_SG_ID=$(aws ec2 create-security-group \
  --group-name hotel-ecs-sg \
  --description "Hotel API ECS task access from ALB only" \
  --vpc-id "$VPC_ID" \
  --query GroupId \
  --output text)

aws ec2 authorize-security-group-ingress \
  --group-id "$ECS_SG_ID" \
  --protocol tcp \
  --port 8080 \
  --source-group "$ALB_SG_ID"
```

RDS security group. It accepts MySQL only from ECS:

```bash
export RDS_SG_ID=$(aws ec2 create-security-group \
  --group-name hotel-rds-sg \
  --description "Hotel API RDS MySQL access from ECS only" \
  --vpc-id "$VPC_ID" \
  --query GroupId \
  --output text)

aws ec2 authorize-security-group-ingress \
  --group-id "$RDS_SG_ID" \
  --protocol tcp \
  --port 3306 \
  --source-group "$ECS_SG_ID"
```

## 6. Create RDS MySQL

Create a DB subnet group:

```bash
aws rds create-db-subnet-group \
  --db-subnet-group-name hotel-db-subnet-group \
  --db-subnet-group-description "Hotel API DB subnet group" \
  --subnet-ids "$PRIVATE_SUBNET_1" "$PRIVATE_SUBNET_2"
```

Create a small MySQL database:

```bash
aws rds create-db-instance \
  --db-instance-identifier hotel-booking-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --allocated-storage 20 \
  --db-name "$DB_NAME" \
  --master-username "$DB_USERNAME" \
  --master-user-password "$DB_PASSWORD" \
  --vpc-security-group-ids "$RDS_SG_ID" \
  --db-subnet-group-name hotel-db-subnet-group \
  --backup-retention-period 0 \
  --no-publicly-accessible
```

Wait until available:

```bash
aws rds wait db-instance-available \
  --db-instance-identifier hotel-booking-db
```

Get endpoint:

```bash
export RDS_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier hotel-booking-db \
  --query "DBInstances[0].Endpoint.Address" \
  --output text)

export DB_URL="jdbc:mysql://$RDS_ENDPOINT:3306/$DB_NAME?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
echo "$DB_URL"
```

If RDS is unavailable in Learner Lab, run MySQL on EC2 as a fallback and set `DB_URL` to that EC2 private IP. Document the limitation in the final report.

## 7. Create Secrets Manager Secrets

Database password:

```bash
export DB_PASSWORD_SECRET_ARN=$(aws secretsmanager create-secret \
  --name hotel/db-password \
  --secret-string "$DB_PASSWORD" \
  --query ARN \
  --output text)
```

JWT secret:

```bash
export JWT_SECRET_ARN=$(aws secretsmanager create-secret \
  --name hotel/jwt-secret \
  --secret-string "$JWT_SECRET" \
  --query ARN \
  --output text)
```

## 8. Create CloudWatch Log Group

```bash
aws logs create-log-group --log-group-name /ecs/hotel-booking-backend
```

Optional retention:

```bash
aws logs put-retention-policy \
  --log-group-name /ecs/hotel-booking-backend \
  --retention-in-days 7
```

## 9. Create ECS Cluster

```bash
aws ecs create-cluster --cluster-name hotel-booking-cluster
```

## 10. Create IAM Task Execution Role

Check whether the standard ECS task execution role exists:

```bash
aws iam get-role --role-name ecsTaskExecutionRole
```

If it does not exist:

```bash
cat > ecs-task-trust-policy.json <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "ecs-tasks.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
JSON

aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-task-trust-policy.json

aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

Allow the role to read the two Secrets Manager secrets:

```bash
cat > ecs-secrets-policy.json <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "$DB_PASSWORD_SECRET_ARN",
        "$JWT_SECRET_ARN"
      ]
    }
  ]
}
JSON

aws iam put-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name hotel-secrets-read \
  --policy-document file://ecs-secrets-policy.json
```

## 11. Create ECS Task Definition

Create `task-definition.json`:

```bash
cat > task-definition.json <<JSON
{
  "family": "hotel-booking-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::$AWS_ACCOUNT_ID:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "hotel-booking-backend",
      "image": "$IMAGE_URI",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        { "name": "PORT", "value": "8080" },
        { "name": "DB_URL", "value": "$DB_URL" },
        { "name": "DB_USERNAME", "value": "$DB_USERNAME" },
        { "name": "UPLOAD_DIR", "value": "/app/uploads/photos" },
        { "name": "SWAGGER_ENABLED", "value": "true" }
      ],
      "secrets": [
        {
          "name": "DB_PASSWORD",
          "valueFrom": "$DB_PASSWORD_SECRET_ARN"
        },
        {
          "name": "JWT_SECRET",
          "valueFrom": "$JWT_SECRET_ARN"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/hotel-booking-backend",
          "awslogs-region": "$AWS_REGION",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
JSON
```

Register it:

```bash
aws ecs register-task-definition \
  --cli-input-json file://task-definition.json
```

Health checking is handled by the ALB target group using `GET /actuator/health`. This avoids depending on shell tools such as `curl` inside the Java runtime image.

## 12. Create ALB Target Group

Target type must be `ip` for Fargate:

```bash
export TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
  --name hotel-api-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id "$VPC_ID" \
  --target-type ip \
  --health-check-protocol HTTP \
  --health-check-path /actuator/health \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --matcher HttpCode=200 \
  --query "TargetGroups[0].TargetGroupArn" \
  --output text)
```

## 13. Create Application Load Balancer

```bash
export ALB_ARN=$(aws elbv2 create-load-balancer \
  --name hotel-api-alb \
  --subnets "$PUBLIC_SUBNET_1" "$PUBLIC_SUBNET_2" \
  --security-groups "$ALB_SG_ID" \
  --scheme internet-facing \
  --type application \
  --query "LoadBalancers[0].LoadBalancerArn" \
  --output text)
```

Create HTTP listener:

```bash
aws elbv2 create-listener \
  --load-balancer-arn "$ALB_ARN" \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN"
```

Get ALB DNS:

```bash
export ALB_DNS=$(aws elbv2 describe-load-balancers \
  --load-balancer-arns "$ALB_ARN" \
  --query "LoadBalancers[0].DNSName" \
  --output text)

echo "http://$ALB_DNS"
```

## 14. Create ECS Service

Use public IP assignment only if your ECS tasks run in public subnets. If using private subnets with NAT, set `assignPublicIp=DISABLED`.

```bash
aws ecs create-service \
  --cluster hotel-booking-cluster \
  --service-name hotel-booking-service \
  --task-definition hotel-booking-backend \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$PUBLIC_SUBNET_1,$PUBLIC_SUBNET_2],securityGroups=[$ECS_SG_ID],assignPublicIp=ENABLED}" \
  --load-balancers "targetGroupArn=$TARGET_GROUP_ARN,containerName=hotel-booking-backend,containerPort=8080"
```

Wait for service stability:

```bash
aws ecs wait services-stable \
  --cluster hotel-booking-cluster \
  --services hotel-booking-service
```

## 15. Test Deployment

Health check:

```bash
curl "http://$ALB_DNS/actuator/health"
```

Expected:

```json
{"status":"UP"}
```

Swagger:

```text
http://ALB_DNS/swagger-ui.html
```

Postman:

1. Import `Hotel Booking API - Updated Current.postman_collection.json`.
2. Set collection variable `baseUrl` to:

```text
http://ALB_DNS
```

3. Run the collection folders in order.
4. Manually choose files for multipart photo upload requests.

## 16. AWS Console Steps

Use these if you prefer the console:

1. ECR
   - Create repository: `hotel-booking-backend`
   - Push image using the repository push commands.
2. RDS
   - Create MySQL database.
   - DB name: `hotel_booking`
   - Instance class: smallest allowed by Learner Lab.
   - Security group: RDS SG, inbound `3306` only from ECS SG.
3. Secrets Manager
   - Secret `hotel/db-password`: plain DB password string.
   - Secret `hotel/jwt-secret`: Base64 JWT secret string.
4. CloudWatch Logs
   - Log group: `/ecs/hotel-booking-backend`
5. ECS
   - Create cluster.
   - Create Fargate task definition.
   - Container image: ECR image URI.
   - Container port: `8080`.
   - Environment:
     - `PORT=8080`
     - `DB_URL=jdbc:mysql://RDS_ENDPOINT:3306/hotel_booking?...`
     - `DB_USERNAME=hotel_admin`
     - `UPLOAD_DIR=/app/uploads/photos`
     - `SWAGGER_ENABLED=true`
   - Secrets:
     - `DB_PASSWORD` from Secrets Manager
     - `JWT_SECRET` from Secrets Manager
   - Logs:
     - Driver: `awslogs`
     - Group: `/ecs/hotel-booking-backend`
     - Stream prefix: `ecs`
6. EC2 Load Balancing
   - Create target group.
   - Type: `ip`
   - Protocol: HTTP
   - Port: `8080`
   - Health path: `/actuator/health`
   - Create internet-facing ALB.
   - Listener: HTTP `80`, forward to target group.
7. ECS Service
   - Launch type: Fargate.
   - Desired tasks: `1`.
   - Attach ALB target group.
   - Security group: ECS SG.

## 17. Redeploy After Code Changes

Build and push a new image:

```bash
docker build -t "$APP_NAME:$IMAGE_TAG" .
docker tag "$APP_NAME:$IMAGE_TAG" "$IMAGE_URI"
docker push "$IMAGE_URI"
```

Force ECS to pull the new `latest` image:

```bash
aws ecs update-service \
  --cluster hotel-booking-cluster \
  --service hotel-booking-service \
  --force-new-deployment
```

Watch rollout:

```bash
aws ecs wait services-stable \
  --cluster hotel-booking-cluster \
  --services hotel-booking-service
```

Check health:

```bash
curl "http://$ALB_DNS/actuator/health"
```

For a cleaner production-style release, use immutable tags:

```bash
export IMAGE_TAG=$(git rev-parse --short HEAD)
```

Then register a new task definition revision with that image tag and update the ECS service to the new revision.

## 18. Observability Evidence

For the report/demo, capture:

- ALB DNS health response screenshot.
- ECS service running task screenshot.
- Target group healthy target screenshot.
- CloudWatch log stream screenshot.
- `/actuator/health` response.
- Optional:
  - `/actuator/prometheus`
  - `/actuator/metrics`

CloudWatch logs command:

```bash
aws logs tail /ecs/hotel-booking-backend --follow
```

## 19. Cost Estimate

These are rough assumptions for a short course demo. AWS Academy Learner Lab pricing and service availability may differ.

### ECS Fargate

Assumption:

- 1 task
- 0.5 vCPU
- 1 GB memory
- Runs only during demo/testing

Cost driver:

- Charged per vCPU-hour and GB-hour.

Reduction ideas:

- Desired count `1`.
- Stop the ECS service after demo by setting desired count to `0`.
- Avoid running multiple task copies.

### Application Load Balancer

Assumption:

- 1 ALB
- Low traffic

Cost driver:

- ALB hourly charge plus load balancer capacity units.

Reduction ideas:

- Delete the ALB after demo.
- Use one ALB for only this service.

### RDS MySQL

Assumption:

- Smallest MySQL instance allowed, such as `db.t3.micro`
- 20 GB storage
- No Multi-AZ
- Backup retention `0` for demo

Cost driver:

- Instance hours and storage.

Reduction ideas:

- Stop/delete RDS after demo.
- Disable Multi-AZ.
- Use minimum storage.
- Use EC2-local MySQL as a fallback if RDS is unavailable or too expensive in Learner Lab.

### CloudWatch Logs

Assumption:

- Low log volume
- 7-day retention

Cost driver:

- Log ingestion and storage.

Reduction ideas:

- Keep retention short.
- Avoid debug SQL logs.
- Delete log group after demo.

### Secrets Manager

Assumption:

- 2 secrets:
  - DB password
  - JWT secret

Cost driver:

- Per-secret monthly cost and API calls.

Reduction ideas:

- Delete secrets after demo.
- For a very short classroom demo, ECS environment variables can be used, but Secrets Manager is the better professional option and matches the requirement.

## 20. Cleanup After Demo

To reduce cost:

```bash
aws ecs update-service \
  --cluster hotel-booking-cluster \
  --service hotel-booking-service \
  --desired-count 0
```

Then delete in this order when done:

1. ECS service
2. ALB listener
3. ALB
4. Target group
5. RDS instance
6. Secrets
7. ECR images/repository
8. CloudWatch log group
9. Security groups
