package eu.darken.capod.profiles.core

class AddressAlreadyClaimedException(
    val address: String,
    val claimedByProfileLabel: String,
) : IllegalStateException("Address $address is already used by profile '$claimedByProfileLabel'")
