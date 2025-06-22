package io.github.turtlepaw.mindsky

import io.github.turtlepaw.mindsky.db.EngagementType
import io.objectbox.converter.*


class EngagementTypeConverter : PropertyConverter<EngagementType?, Int?> {
    override fun convertToEntityProperty(databaseValue: Int?): EngagementType? {
        if (databaseValue == null) {
            return null
        }
        return EngagementType.entries.elementAt(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: EngagementType?): Int? {
        return entityProperty?.ordinal
    }
}