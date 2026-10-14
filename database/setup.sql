-- Run once as a Postgres superuser (e.g. psql -U postgres -f database/setup.sql)
CREATE USER flags WITH PASSWORD 'flags';
CREATE DATABASE flags OWNER flags;
