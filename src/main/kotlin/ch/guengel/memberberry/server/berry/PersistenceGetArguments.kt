package ch.guengel.memberberry.server.berry

enum class OrderBy(val fieldName: String) {
    CREATED("created"),
    TITLE("title"),
    PRIORITY("priority"),
    STATE("state")
}

enum class Order {
    ASCENDING,
    DESCENDING
}

data class Pagination(
    var index: Int = 0,
    var size: Int = 25
)

data class Ordering(var orderBy: OrderBy = OrderBy.TITLE, var order: Order = Order.DESCENDING)

data class GetArguments(
    var userId: String = "",
    var pagination: Pagination = Pagination(),
    var ordering: Ordering = Ordering(),
    var inState: String = "",
    var withPriority: String = "",
    var withTag: String = ""
) {

    fun pagination(init: Pagination.() -> Unit) {
        pagination.init()
    }

    fun ordering(init: Ordering.() -> Unit) {
        ordering.init()
    }
}

fun getArguments(init: GetArguments.() -> Unit): GetArguments {
    val getArguments = GetArguments()
    getArguments.init()
    return getArguments
}

