package fr.nekotv

/** Pure carousel navigation policy, shared by TV focus and arrow buttons. */
internal object CarouselNavigation {
    fun pageTarget(current: Int, pageSize: Int, itemCount: Int, direction: Int): Int {
        if (itemCount <= 0) return 0
        val size = pageSize.coerceAtLeast(1)
        return (current + direction.coerceIn(-1, 1) * size).coerceIn(0, itemCount - 1)
    }

    fun keyTarget(current: Int, itemCount: Int, key: CarouselKey): Int = when (key) {
        CarouselKey.LEFT -> pageTarget(current, 1, itemCount, -1)
        CarouselKey.RIGHT -> pageTarget(current, 1, itemCount, 1)
        CarouselKey.ENTER -> current.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    }
}

enum class CarouselKey { LEFT, RIGHT, ENTER }
