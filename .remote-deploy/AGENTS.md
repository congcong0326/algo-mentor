# Remote Environment Access

This repository documents two development virtual machines. Connect through the local SSH aliases below; they use the dedicated project key automatically.

## Purpose

This repository is used to deploy and operate remote services. The current phase is the development-environment deployment of the `algo-mentor` service and its required PostgreSQL database:

1. Deploy and maintain PostgreSQL for the application on `pass-dev`.
2. Deploy the Java application process from `/root/code/algo-mentor` on `leetmentor-dev`.
3. Validate database connectivity, Flyway migrations, application startup, logs, and health checks in development before any production release.

The two machines were verified as application-clean on 2026-08-11: PostgreSQL and the `algo-mentor` deployment directory were not present. Treat all service installation and configuration as new deployment work, not an upgrade of an existing remote service.

| Alias | Host | Role |
| --- | --- | --- |
| `pass-dev` | `192.168.10.121` (`pass`) | PASS database host, planned PostgreSQL deployment |
| `leetmentor-dev` | `192.168.10.118` (`leetmentor`) | Planned `algo-mentor` business-process host |

## Login

```bash
ssh pass-dev
ssh leetmentor-dev
```

The aliases are defined in `/root/.ssh/config` and use the private key at `/root/.ssh/id_ed25519_remote_deploy`. The corresponding public key is installed for remote user `congcong` on both hosts.

Do not add passwords, private keys, tokens, or production credentials to this repository. If key access fails, verify the local private-key permissions (`600`) and the remote `~/.ssh/authorized_keys` entry before using the team-approved credential channel.

## Deployment Baseline

- Read `PostgreSQL-16.14-部署与维护手册.md` before installing or changing PostgreSQL. It is the team's research-backed security and maintenance baseline for PostgreSQL 16.
- Install PostgreSQL only on `pass-dev`, using Ubuntu's supported `postgresql-16` and `postgresql-client-16` packages. Stay on the PostgreSQL 16 release line and apply supported 16.x security updates; do not permanently pin an obsolete patch release.
- Before accepting database traffic, configure least-privilege application roles, SCRAM authentication, restricted `listen_addresses` and `pg_hba.conf`, and firewall rules that permit only necessary sources. Do not expose PostgreSQL directly to the public network.
- Configure backup, WAL archiving, monitoring, and a tested recovery path before treating the database as production-ready. The PostgreSQL handbook defines the required acceptance checks.
- Deploy `algo-mentor` to `/root/code/algo-mentor` on `leetmentor-dev`. Build and test the Java application from the source repository, supply database settings and secrets through protected runtime configuration, and run it under a managed service account rather than as an interactive shell process.
- Do not run database migrations, package installation, service restarts, data deletion, firewall changes, or other state-changing remote commands without first confirming the target host, backup status, and intended scope.
- This phase is development only. Promote to production only after the development deployment and tests are complete and the production target and release approval are explicitly provided.

## Deployment Order

1. Review the PostgreSQL handbook and record capacity, network access, RPO, RTO, backup location, and responsible owner.
2. Provision and harden PostgreSQL 16 on `pass-dev`; create a dedicated least-privilege application database and roles.
3. Deploy the `algo-mentor` Java process on `leetmentor-dev`, with protected configuration for its database connection and other secrets.
4. Run Flyway migrations and the application's database connectivity and health checks.
5. Verify logs, monitoring, backups, and a rollback or recovery procedure; then update `README.md` with the actual deployed state.

## Guardrails

- These are development hosts. Do not treat either as a production target.
- Begin remote work with read-only inspection. Confirm the target host before changing services, data, packages, or firewall rules.
- The current environment baseline is recorded in `README.md`; update it after any deployment or infrastructure change.
- The planned `algo-mentor` location is `/root/code/algo-mentor` on `leetmentor-dev`. It was absent during the 2026-08-11 verification.
