package com.rematerial.app.core.commerce

sealed interface CommerceResult<out T> {
    data class Success<T>(val value: T) : CommerceResult<T>
    data class Failure(val error: CommerceError) : CommerceResult<Nothing>
}

sealed interface CommerceError {
    data object ProductNotFound : CommerceError
    data object ListingUnavailable : CommerceError
    data object OutOfStock : CommerceError
    data object EmptyCart : CommerceError
    data object DifferentSeller : CommerceError
    data object OrderNotFound : CommerceError
    data object InvalidTransition : CommerceError
    data object SellerNotVerified : CommerceError
    data class InvalidInput(val fields: List<String>) : CommerceError
    data object MediaImportFailed : CommerceError
}

enum class ListingState(val label: String) {
    DRAFT("Draft"), PUBLISHED("Tayang"), PAUSED("Dijeda"), SOLD_OUT("Stok habis"),
    ARCHIVED("Diarsipkan"), REMOVED("Dihapus"),
}
