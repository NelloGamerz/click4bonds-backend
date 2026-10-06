# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean package -Pprod -DskipTests

# Runtime stage
#
# Debian rather than Alpine, because Alpine has no LibreOffice package and the
# application renders deal confirmation letters with a headless `soffice`.
# Everything else about this stage is unchanged except the font packages: a
# LibreOffice with no fonts renders every character as an empty box, which is
# the most common way a headless PDF conversion silently produces rubbish.
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        libreoffice-calc \
        fonts-dejavu-core \
        fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

# LibreOffice writes caches and lock files outside its profile directory and
# fails opaquely when HOME is unwritable. The converter redirects it to a private
# directory per conversion; this is the fallback for anything it does not.
ENV HOME=/tmp

COPY --from=build /app/target/clickforbonds.jar clickforbonds.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1


ENTRYPOINT ["java", "-jar", "clickforbonds.jar"]
