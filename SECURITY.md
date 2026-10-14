# Security Policy

## Supported Versions

Security fixes are provided for the latest published version of Fatfingert on
[Modrinth](https://modrinth.com/mod/fatfingert) and the matching `main` branch
on GitHub. Please make sure you are testing against the latest release before
reporting an issue.

## Reporting a Vulnerability

If you believe you have found a security vulnerability in Fatfingert, please
report it privately using one of these channels:

- **Preferred:** [GitHub's private vulnerability reporting](https://github.com/secretnarwhal/Fatfingert/security/advisories)
  for this repository.
- Alternatively, open a **draft** GitHub security advisory or a private issue and
  mention `@secretnarwhal`.

Please include:

1. The Fatfingert version and Minecraft version you are running.
2. A description of the vulnerability and its impact (e.g., crash, information
   disclosure, unintended item interaction).
3. Steps to reproduce or a proof of concept, if possible.

## What to Expect

- You will receive an acknowledgment within **7 days**.
- We will investigate and keep you informed of progress.
- If the vulnerability is confirmed and fixed, you will be **credited by name**
  in the release notes and the GitHub security advisory, unless you prefer to
  remain anonymous.
- Please give us up to **90 days** to address the issue before any public
  disclosure. Coordinated disclosure is appreciated.

## Scope

- Fatfingert client mod source code in this repository (`src/`).
- Build and packaging configuration that could affect users of published releases
  (e.g., a compromised dependency, not user-side misconfiguration).

Out of scope: bugs in Minecraft itself, Fabric Loader, or other mods;
self-inflicted configuration mistakes.

## Security Contact

Security maintainer: **secretnarwhal** (AlmondsMilk on Modrinth)
