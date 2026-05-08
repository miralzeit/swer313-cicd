# AWS Academy Learner Lab Deployment

This guide deploys the Spring Boot hotel backend on an EC2 instance. Use Amazon RDS MySQL if your Learner Lab allows it. If not, run MySQL on the same EC2 instance for a simple course demo.

## 1. Build The Jar

On your local machine or EC2:

```bash
mvn clean package
```

The jar is created at:

```bash
target/rest-0.0.1-SNAPSHOT.jar
```

## 2. Required Environment Variables

Set these before starting the app:

```bash
export PORT=8080
export DB_URL='jdbc:mysql://localhost:3306/hotel_booking?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USERNAME='root'
export DB_PASSWORD='your_mysql_password'
export JWT_SECRET='base64_32_byte_or_longer_secret'
export UPLOAD_DIR='/home/ec2-user/hotel-backend/uploads/photos'
export SWAGGER_ENABLED=true
```

`JWT_SECRET` must be Base64. To generate one on Linux:

```bash
openssl rand -base64 32
```

For RDS, replace `localhost` in `DB_URL` with the RDS endpoint:

```bash
export DB_URL='jdbc:mysql://your-rds-endpoint.amazonaws.com:3306/hotel_booking?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
```

## 3. Option A: MySQL On EC2

Install and start MySQL:

```bash
sudo dnf update -y
sudo dnf install -y mysql-server
sudo systemctl enable mysqld
sudo systemctl start mysqld
```

Create the database:

```bash
mysql -u root -p
```

```sql
CREATE DATABASE IF NOT EXISTS hotel_booking;
```

## 4. Option B: MySQL On RDS

Create an RDS MySQL database if Learner Lab allows it.

Security group rules:

- EC2 security group: allow inbound TCP `8080` from your IP for the API.
- RDS security group: allow inbound TCP `3306` from the EC2 security group.

Use the RDS endpoint in `DB_URL`.

## 5. Start The App

Create the upload directory:

```bash
mkdir -p "$UPLOAD_DIR"
```

Run the jar:

```bash
java -jar target/rest-0.0.1-SNAPSHOT.jar
```

For a background process:

```bash
nohup java -jar target/rest-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
```

Check logs:

```bash
tail -f app.log
```

## 6. Open Security Group Port

In the EC2 security group, add inbound rule:

- Type: Custom TCP
- Port: `8080`
- Source: your IP, or `0.0.0.0/0` only for a short demo

Then test:

```bash
curl http://EC2_PUBLIC_IP:8080/actuator/health
```

Expected:

```json
{"status":"UP"}
```

Swagger, if enabled:

```text
http://EC2_PUBLIC_IP:8080/swagger-ui.html
```

## 7. Local Docker Test

From the project root:

```bash
docker compose up --build
```

Test:

```bash
curl http://localhost:8080/actuator/health
```

Stop:

```bash
docker compose down
```

To remove local MySQL/upload volumes:

```bash
docker compose down -v
```

## 8. Redeploy After Code Changes

On EC2:

```bash
git pull
mvn clean package
pkill -f 'rest-0.0.1-SNAPSHOT.jar' || true
nohup java -jar target/rest-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
tail -f app.log
```

If using Docker:

```bash
git pull
docker compose up --build -d
docker compose logs -f app
```

## 9. Common Errors

`Access denied for user`

- Check `DB_USERNAME` and `DB_PASSWORD`.
- If using RDS, confirm the user exists in RDS.

`Communications link failure`

- Check `DB_URL`.
- Confirm MySQL is running.
- Confirm security group access to port `3306`.

`Failed to bind to port`

- Another process is already using the port.
- Change `PORT`, or stop the old process with `pkill -f 'rest-0.0.1-SNAPSHOT.jar'`.

`JWT secret` or token startup errors

- Set `JWT_SECRET` to a valid Base64 secret.
- Generate one with `openssl rand -base64 32`.

Uploaded photos are missing after restart

- Make sure `UPLOAD_DIR` points to a persistent folder.
- If using Docker, keep the `uploads` volume.

Payment retry still fails after a failed payment

- Existing databases may still have an old unique index on `payment.booking_id`.
- See `docs/payment-retry-mysql-migration.md`.
