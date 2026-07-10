package eu.darken.capod.common.upgrade.core.client

// The user backed out of the Google Play payment sheet — expected control-flow outcome,
// handled silently by the UI layer, never shown as an error.
class UserCanceledBillingException(cause: Throwable) : Exception("User canceled the billing flow.", cause)
