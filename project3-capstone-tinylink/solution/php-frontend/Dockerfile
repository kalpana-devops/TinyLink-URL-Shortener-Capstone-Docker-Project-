# Single-stage on purpose: there's no build/compile step for this plain PHP
# app (no Composer dependencies here), so a multi-stage split would add
# nothing. Contrast this with java-backend/Dockerfile, which genuinely
# needs multiple stages because Maven compiles source into a JAR.
FROM php:8.2-apache
COPY src/ /var/www/html/
EXPOSE 80
