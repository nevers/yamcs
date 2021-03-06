AuthModules
===========

.. toctree::
    :maxdepth: 1

    yaml
    ldap
    spnego


The security subsystem is modular by design and allows combining different AuthModules together. This allows for scenarios where for example you want to authenticate via LDAP, but determine privileges via YAML files.

The default set of AuthModules include:

:doc:`yaml`
    Reads Yaml files to verify the credentials of the user, or assign privileges.
:doc:`ldap`
    Attempts to bind to LDAP with the provided credentials. Also capable of reading privileges for the user.
:doc:`spnego`
    Supports authenticating against a Kerberos server. This module includes extra support for Single-Sign-On via the Yamcs web interface.

AuthModules have an order. When a login attempt is made, AuthModules are iterated a first time in this order. Each AuthModule is asked if it can authenticate with the provided credentials. The first matching AuthModule contributes the user principal. A second iteration is done to then contribute privileges to the identified user. During both iterations, AuthModules reserve the right to halt the global login process for any reason.
