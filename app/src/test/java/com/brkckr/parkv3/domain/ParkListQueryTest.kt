package com.brkckr.parkv3.domain

import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.testutil.park
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParkListQueryTest {

    private val available = park(1, name = "Kadıköy Rıhtım", district = "KADIKÖY", emptyCapacity = 12)
    private val full = park(2, name = "Moda Sahil", district = "KADIKÖY", emptyCapacity = 0)
    private val missingOccupancy = park(3, name = "Şişli Merkez", district = "ŞİŞLİ", capacity = null, emptyCapacity = null)
    private val spaceWithoutCapacity = park(4, name = "İstinye Yolu", district = "SARIYER", capacity = null, emptyCapacity = 30)
    private val inconsistent = park(5, name = "Üsküdar Meydan", district = "ÜSKÜDAR", capacity = 10, emptyCapacity = 50)
    private val all = listOf(available, full, missingOccupancy, spaceWithoutCapacity, inconsistent)

    private fun ids(query: String = "", filters: ParkFilters = ParkFilters(), favorites: Set<Int> = emptySet()) =
        ParkListQuery.apply(all, favorites, query, filters, reference = null).map { it.park.id }

    @Test
    fun `available filter needs consistent free spaces`() {
        assertThat(ids(filters = ParkFilters(availableOnly = true))).containsExactly(1)
    }

    @Test
    fun `filters combine with AND`() {
        val favorites = setOf(2, 3, 1)
        assertThat(ids(filters = ParkFilters(favoritesOnly = true), favorites = favorites)).containsExactly(1, 2, 3)
        assertThat(ids(filters = ParkFilters(availableOnly = true, favoritesOnly = true), favorites = setOf(2, 3))).isEmpty()
        assertThat(ids(query = "kadıköy", filters = ParkFilters(availableOnly = true, favoritesOnly = true), favorites = favorites))
            .containsExactly(1)
    }

    @Test
    fun `search ignores Turkish casing and diacritics in both directions`() {
        assertThat(ids("kadikoy")).containsExactly(1, 2)
        assertThat(ids("KADIKÖY")).containsExactly(1, 2)
        assertThat(ids("istinye")).containsExactly(4)
        assertThat(ids("ISTINYE")).containsExactly(4)
        assertThat(ids("İSTİNYE")).containsExactly(4)
        assertThat(ids("sisli")).containsExactly(3)
        assertThat(ids("üsküdar")).containsExactly(5)
        assertThat(ids("uskudar")).containsExactly(5)
    }

    @Test
    fun `search matches district and every token`() {
        assertThat(ids("sarıyer")).containsExactly(4)
        assertThat(ids("  moda   kadikoy ")).containsExactly(2)
        assertThat(ids("moda şişli")).isEmpty()
        assertThat(ids("")).hasSize(5)
    }

    @Test
    fun `items carry favorite flag`() {
        val items = ParkListQuery.apply(all, setOf(3), "", ParkFilters(), reference = null)
        assertThat(items.single { it.isFavorite }.park.id).isEqualTo(3)
    }

    @Test
    fun `without a reference parks are sorted by Turkish alphabetical order`() {
        val parks = listOf(park(1, name = "Çamlıca"), park(2, name = "Cevizli"), park(3, name = "Zeytinburnu"), park(4, name = "Ödemiş"), park(5, name = "Osmanbey"))
        val names = ParkListQuery.apply(parks, emptySet(), "", ParkFilters(), reference = null).map { it.park.name }
        assertThat(names).containsExactly("Cevizli", "Çamlıca", "Osmanbey", "Ödemiş", "Zeytinburnu").inOrder()
    }

    @Test
    fun `with a reference parks are sorted by distance and parks without location go last`() {
        val taksim = GeoPoint(41.0369, 28.9850)
        val near = park(1, name = "B Near", location = GeoPoint(41.0400, 28.9860))
        val far = park(2, name = "A Far", location = GeoPoint(40.9903, 29.0236))
        val noLocation = park(3, name = "0 Unknown", location = null)
        val items = ParkListQuery.apply(listOf(far, noLocation, near), emptySet(), "", ParkFilters(), reference = taksim)
        assertThat(items.map { it.park.id }).containsExactly(1, 2, 3).inOrder()
        assertThat(items.last().distanceMeters).isNull()
        // Taksim to Kadıköy pier is roughly 6 km as the crow flies.
        assertThat(items[1].distanceMeters!!).isWithin(500.0).of(6_200.0)
    }
}
