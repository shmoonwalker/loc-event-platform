package nl.loc.backend.event.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/** Query-string binding for {@code GET /api/events}. */
@Schema(description = "Query string for GET /api/events. page starts at 0. size defaults to 20 and cannot exceed 20.")
public class EventsQuery {

    @Parameter(
            name = "q",
            in = ParameterIn.QUERY,
            required = false,
            description = "Free text. Matched with websearch_to_tsquery on title, description, venue, city, "
                    + "categories, and tags. At most 100 characters. When present, the default sort is relevance.",
            example = "jazz")
    private String q;

    @Parameter(
            name = "city",
            in = ParameterIn.QUERY,
            required = false,
            description = "City slug. Limits rows to that city. Example: amsterdam.",
            example = "amsterdam")
    private String city;

    @Parameter(
            name = "category",
            in = ParameterIn.QUERY,
            required = false,
            description = "Category slug. Repeat the parameter to combine categories with OR. Unknown slugs return 400.",
            example = "music-nightlife",
            array = @ArraySchema(schema = @Schema(
                    example = "music-nightlife")))
    private List<String> category = new ArrayList<>();

    @Parameter(
            name = "tag",
            in = ParameterIn.QUERY,
            required = false,
            description = "Tag slug. Repeat the parameter to require every tag (AND). Unknown slugs return 400.",
            example = "jazz",
            array = @ArraySchema(schema = @Schema(
                    example = "jazz")))
    private List<String> tag = new ArrayList<>();

    @Parameter(
            name = "when",
            in = ParameterIn.QUERY,
            required = false,
            description = "Time window in Europe/Amsterdam. tonight is 18:00 until midnight, weekend is Saturday 00:00 "
                    + "through Monday 00:00, upcoming is the next 30 days. Unknown values return 400.",
            example = "tonight",
            schema = @Schema(allowableValues = {"tonight", "weekend", "upcoming"}))
    private String when;

    @Parameter(
            name = "place",
            in = ParameterIn.QUERY,
            required = false,
            description = "online selects location.type ONLINE. physical selects location.type PHYSICAL. "
                    + "Unknown values return 400.",
            example = "physical",
            schema = @Schema(allowableValues = {"physical", "online"}))
    private String place;

    @Parameter(
            name = "sort",
            in = ParameterIn.QUERY,
            required = false,
            description = "start_time orders by start time then id. relevance orders by search rank, then start time, "
                    + "then id. Defaults to relevance when q is present, otherwise start_time.",
            example = "start_time",
            schema = @Schema(allowableValues = {"start_time", "relevance"}))
    private String sort;

    @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            required = false,
            description = "Zero-based page index. Defaults to 0. See-all uses 0.",
            example = "0",
            schema = @Schema(minimum = "0", maximum = "2147483647"))
    private Integer page;

    @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            required = false,
            description = "Page size. Defaults to 20. Maximum 20.",
            example = "20",
            schema = @Schema(minimum = "1", maximum = "20"))
    private Integer size;

    @Parameter(description = "Inclusive first start date in Europe/Amsterdam. Requires dateTo; cannot combine with when.", example = "2030-06-08")
    private String dateFrom;

    @Parameter(description = "Inclusive last start date in Europe/Amsterdam. Requires dateFrom.", example = "2030-06-10")
    private String dateTo;

    @Parameter(description = "Inclusive local start time HH:mm. Requires timeTo. Applies on each selected date.", example = "18:00")
    private String timeFrom;

    @Parameter(description = "Exclusive local start time HH:mm. Earlier than timeFrom means overnight; equal times are invalid.", example = "23:00")
    private String timeTo;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public List<String> getCategory() {
        return category;
    }

    public void setCategory(List<String> category) {
        this.category = category;
    }

    public List<String> getTag() {
        return tag;
    }

    public void setTag(List<String> tag) {
        this.tag = tag;
    }

    public String getWhen() {
        return when;
    }

    public void setWhen(String when) {
        this.when = when;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

    public String getDateTo() {
        return dateTo;
    }

    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }

    public String getTimeFrom() {
        return timeFrom;
    }

    public void setTimeFrom(String timeFrom) {
        this.timeFrom = timeFrom;
    }

    public String getTimeTo() {
        return timeTo;
    }

    public void setTimeTo(String timeTo) {
        this.timeTo = timeTo;
    }
}
