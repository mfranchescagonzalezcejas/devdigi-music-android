# Navidrome Recent Albums Specification

## ADDED Requirements

### Requirement: Account-scoped recent album catalogue

The application MUST load recent albums only for the current authenticated
`ServerAccountIdentity`.

A successful catalogue result MUST retain the identity that owns the data.

#### Scenario: Catalogue belongs to authenticated account

- GIVEN authenticated account A
- WHEN recent albums are loaded successfully for account A
- THEN the returned catalogue MUST identify account A as its owner
- AND it MUST NOT be implicitly reusable for account B

### Requirement: Minimal album model

The recent-albums domain model MUST contain the opaque album id and title.

Artist and cover-art id MAY be absent.

Transport objects, credentials, authentication tokens, salts and server URLs
MUST NOT be stored in album domain objects.

#### Scenario: Optional metadata is absent

- GIVEN a valid recent album with id and title
- AND artist or cover-art metadata is absent
- WHEN the response is parsed
- THEN the album MUST remain usable with the optional fields absent

### Requirement: Strict OpenSubsonic response parsing

The recent-albums parser MUST accept a valid OpenSubsonic
`subsonic-response` success envelope containing `albumList2`.

Album order MUST match server order.

The parser MUST reject malformed JSON, malformed envelopes, wrong field
types, blank album ids and blank album titles.

The parser MUST bound response characters and structural nesting before materializing the JSON tree.

#### Scenario: Parse ordered recent albums

- GIVEN a valid success response containing albums A then B
- WHEN the response is parsed
- THEN the result MUST contain A then B in that order

#### Scenario: Parse an empty catalogue

- GIVEN a valid success response whose `albumList2` has no albums
- WHEN the response is parsed
- THEN parsing MUST succeed with an empty list

#### Scenario: Reject malformed album data

- GIVEN a success response containing an album with a missing or blank id
- WHEN the response is parsed
- THEN the result MUST be malformed response

### Requirement: Distinct failure taxonomy

A well-formed Subsonic failure with error code 40 MUST map to authentication
required.

Other well-formed server failures MUST remain distinguishable from malformed
protocol data.

Network failures SHALL be represented independently by the repository
boundary added in WU2.

#### Scenario: Authentication is rejected

- GIVEN a well-formed failed response with error code 40
- WHEN the response is parsed
- THEN the result MUST indicate authentication required

#### Scenario: Server reports another failure

- GIVEN a well-formed failed response with an error code other than 40
- WHEN the response is parsed
- THEN the result MUST indicate server error
- AND MUST NOT be classified as malformed response
