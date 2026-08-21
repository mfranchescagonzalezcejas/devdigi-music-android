# Navidrome Server Connection Specification

## Purpose

Define #13's safe, non-secret endpoint boundary without asserting connection, compatibility, or authentication before #14 supplies authenticated requests.

## Requirements

### Requirement: Endpoint Validation and Normalization

The system MUST parse and normalize an absolute endpoint without network I/O. It MUST reject missing hosts, user-info, queries, fragments, local-domain hosts, and loopback, private, link-local, or unspecified IP hosts, except the exact debug HTTP allowance below. It MUST preserve reverse-proxy paths, canonicalize scheme and host case, and omit HTTPS port 443.

#### Scenario: Normalize a reverse-proxy endpoint

- GIVEN `HTTPS://music.example.com:443/navidrome`
- WHEN it is normalized
- THEN the endpoint is `https://music.example.com/navidrome`
- AND no network request is made

#### Scenario: Reject malformed input

- GIVEN an endpoint with user-info, a query, a fragment, or a prohibited host
- WHEN it is validated
- THEN validation MUST fail
- AND no profile is created

### Requirement: Endpoint-only Server Profile Repository

`ServerProfile` MUST be immutable and contain only its normalized endpoint. A concrete endpoint-only `ServerProfileRepository` MUST persist, restore and revalidate, replace, and delete that endpoint; it MUST discard malformed stored data. It MUST NEVER persist raw input, credentials, tokens, salts, cookies, identities, ping facts, or account/authentication state. `AccountProfile` SHALL remain separate.

#### Scenario: Restore a valid stored endpoint

- GIVEN a normalized stored endpoint
- WHEN the repository restores it
- THEN it returns a revalidated profile containing that endpoint only

#### Scenario: Replace, delete, or reject storage

- GIVEN an existing profile or malformed stored value
- WHEN the repository replaces, deletes, or restores it
- THEN replacement retains only the new normalized endpoint, deletion removes it, and malformed data is discarded

### Requirement: Variant Transport Admission

Build-variant endpoint policy MUST be enforced at admission. Release MUST accept HTTPS only, reject all HTTP, and have no cleartext transport configuration. Debug MUST allow HTTP only for `localhost`, `127.0.0.1`, and `10.0.2.2`, reject all remote HTTP, and use a narrow debug-only cleartext transport configuration. This policy MUST NOT authorize a #13 request.

#### Scenario: Admit permitted local debug HTTP

- GIVEN the debug variant and `http://10.0.2.2:4533`
- WHEN the endpoint is admitted
- THEN admission MUST succeed without issuing a request

#### Scenario: Reject HTTP outside the allowance

- GIVEN release HTTP or debug HTTP to a remote host
- WHEN the endpoint is admitted
- THEN admission MUST fail

### Requirement: Ping Client Verification Facts

The system MUST model URL validity, reachability, compatibility, and authentication independently, and `PingClient` MUST support synthetic outcomes. Until #14 supplies authenticated requests, compatibility and authentication MUST remain `NotChecked`; #13 MUST NOT issue a production unauthenticated ping or infer either fact from a response, transport result, error, or no request.

#### Scenario: Report reachability without compatibility

- GIVEN a synthetic reachable transport without authenticated ping
- WHEN verification state is produced
- THEN reachability MAY be reported
- AND compatibility and authentication MUST be `NotChecked`

#### Scenario: Preserve the authenticated-ping boundary

- GIVEN an unauthenticated response or transport error
- WHEN verification state is produced
- THEN compatibility MUST NOT be compatible
- AND authentication MUST NOT be authenticated

### Requirement: Local Synthetic Verification

Tests MUST use only pure tests, fakes, or local mock HTTP with synthetic fixtures. They MUST NOT contact real servers, private infrastructure, other local services, or user endpoints, and MUST NOT introduce Navidrome Docker or Compose into #13.

#### Scenario: Run safe verification

- GIVEN the #13 test suite runs
- WHEN endpoint, repository, policy, and ping-state cases execute
- THEN all inputs are synthetic or local mock data

#### Scenario: Exclude infrastructure bootstrap

- GIVEN #13 verification is prepared
- WHEN test dependencies are selected
- THEN Navidrome Docker and Compose MUST NOT be required
