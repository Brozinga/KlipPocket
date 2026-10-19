package ru.ytkab0bp.beamklipper.serial

// Name shown first for each device in "List USB devices": the product name the device
// itself reports (the one in the USB permission prompt, e.g. "rp2040"), so boards can be
// told apart. Devices that report the same name get a number: "rp2040 (1)", "rp2040 (2)".
// A device that reports no name has an empty label (the line then starts with its VID/PID).
object UsbDeviceLabel {
    fun baseName(productName: String?): String = productName?.trim().orEmpty()

    // Keeps the order of the input; names that occur once (and empty ones) are left as they are.
    fun numbered(names: List<String>): List<String> {
        val total = names.groupingBy { it }.eachCount()
        val seen = HashMap<String, Int>()
        return names.map { name ->
            if (name.isEmpty() || (total[name] ?: 0) <= 1) name
            else {
                val n = (seen[name] ?: 0) + 1
                seen[name] = n
                "$name ($n)"
            }
        }
    }
}
