@auth
Feature: Navidrome authentication
  As a Navidrome user
  I want authentication and session handling to be reliable and secure
  So that access to my server does not leak credentials or restore a stale session

  Background:
    Given the app has no authenticated session

  @sanity @manual @automation-candidate
  Scenario: Saving a compatible server does not authenticate an account
    Given the user has a valid compatible server endpoint
    When the user saves the server
    Then the server profile is available for authentication
    And no authenticated account session is created
    And no account password is persisted by saving the server alone

  @smoke @security @manual @automation-candidate
  Scenario: Sign in with valid credentials
    Given a reachable compatible server is saved
    And the user has valid account credentials
    When the user signs in
    Then the server validates the credentials before the session is published
    And an authenticated session is available for the exact server and username
    And the accepted credential is stored through the secure credential boundary
    And the password is no longer retained in the sign-in input
    And no authentication secret is exposed in retained evidence

  @regression @security @manual @automation-candidate
  Scenario: Invalid credentials do not create a durable session
    Given a reachable compatible server is saved
    And the user has invalid account credentials
    When the user attempts to sign in
    Then authentication is rejected
    And no authenticated identity is published
    And the rejected credential is not persisted as a durable session

  @regression @security @manual @automation-candidate
  Scenario: Credential persistence failure fails closed
    Given a reachable compatible server is saved
    And the server accepts the account credentials
    When secure credential persistence fails
    Then no authenticated identity is published
    And the app remains signed out
    And no failed candidate credential is treated as a durable session

  @smoke @regression @real-instance @manual @automation-candidate
  Scenario: A saved session is restored only after fresh authentication
    Given the user previously signed in successfully
    And the matching server profile and secure credential remain available
    When the app starts a new process
    Then the saved credential alone does not publish an authenticated identity
    And the app performs a fresh authenticated server request
    And the authenticated session is restored only after that request succeeds

  @regression @real-instance @manual @automation-candidate
  Scenario: A temporary server outage fails closed without discarding a valid credential
    Given the user previously signed in successfully
    And the matching secure credential remains available
    And the server is temporarily unreachable
    When the app attempts to restore the session
    Then no authenticated identity is exposed
    And the secure credential is retained for a later retry
    When the server becomes reachable and restoration is retried
    Then the session can be restored without entering the password again

  @smoke @regression @security @real-instance @manual @automation-candidate
  Scenario: Sign out clears durable session ownership
    Given the user has an authenticated session
    When the user signs out successfully
    Then the authenticated session is cleared
    And the durable account credential is cleared
    When the app starts a new process
    Then the user remains signed out

  @regression @security @real-instance @manual
  Scenario: Authentication evidence does not expose the password
    Given authentication is validated with a real compatible server
    When the authentication persistence and app process logs are inspected
    Then the plaintext password is absent from retained persistence evidence
    And an encoded copy of the password is absent from retained persistence evidence
    And the plaintext password is absent from retained app-process logs
    And authentication tokens, salts, and sensitive request URLs are not retained as public evidence
