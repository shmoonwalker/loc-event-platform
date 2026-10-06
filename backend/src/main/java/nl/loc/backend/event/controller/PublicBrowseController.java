package nl.loc.backend.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.request.CitiesQuery;
import nl.loc.backend.event.dto.request.EventsQuery;
import nl.loc.backend.event.dto.request.HomeQuery;
import nl.loc.backend.event.dto.response.EventDetail;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.dto.response.FilterOption;
import nl.loc.backend.event.dto.response.OrganizerDetail;
import nl.loc.backend.event.dto.response.HomeView;
import nl.loc.backend.event.dto.response.FilterOptions;
import nl.loc.backend.event.service.EventDetailService;
import nl.loc.backend.event.service.FilterOptionsService;
import nl.loc.backend.event.service.EventBrowseService;
import nl.loc.backend.event.service.HomeService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(
        name = "Discovery",
        description = "Homepage, events collection, and city finder over published discoverable events. "
                + "Device location is not used.")
public class PublicBrowseController {

    private final HomeService homeService;
    private final EventBrowseService browse;
    private final BrowseCriteriaParser parser;
    private final FilterOptionsService filterOptions;
    private final EventDetailService details;

    public PublicBrowseController(HomeService homeService, EventBrowseService browse,
                                  BrowseCriteriaParser parser, FilterOptionsService filterOptions,
                                  EventDetailService details) {
        this.homeService = homeService;
        this.browse = browse;
        this.parser = parser;
        this.filterOptions = filterOptions;
        this.details = details;
    }

    @GetMapping("/home")
    @Operation(
            summary = "Homepage",
            description = "Highlight rails (tonight, weekend, near you, online) of up to six events each. "
                    + "A rail with no matches is omitted, so rails can be empty. Each rail carries the "
                    + "/api/events filters for See all. The event list itself is GET /api/events. "
                    + "city scopes only the near-you rail and defaults to Amsterdam. No GPS.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Non-empty homepage rails.",
                    content = @Content(schema = @Schema(implementation = HomeView.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "city is not a slug such as amsterdam.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public HomeView home(@ParameterObject @ModelAttribute HomeQuery query) {
        return homeService.home(parser.homeCity(query.city()));
    }

    @GetMapping("/events")
    @Operation(
            summary = "Events collection",
            description = "Search, filter, sort, and page upcoming published events. With no parameters this is the "
                    + "homepage list, soonest first. Rail See all passes the rail's filters. page starts at 0. "
                    + "Repeated category is OR. Repeated tag is AND. "
                    + "q is matched with websearch_to_tsquery on title, description, venue, city, categories, and tags. "
                    + "Sort defaults to relevance when q is present, otherwise start time, then id. "
                    + "Custom dateFrom/dateTo are inclusive Amsterdam dates and cannot combine with when. "
                    + "timeFrom/timeTo are local HH:mm start times applied each date; overnight ranges are supported. "
                    + "Both ends of each custom range are required. Ended events remain excluded. "
                    + "city with place=online is invalid. Explicit searches never fall back. Unknown filters return 400.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "One page of events.",
                    content = @Content(schema = @Schema(implementation = EventPage.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Unknown place, category, tag, when, or sort, or page, size, city, or q is invalid.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public EventPage events(@ParameterObject @ModelAttribute EventsQuery query) {
        return browse.search(parser.parse(query));
    }

    @GetMapping("/events/filter-options")
    @Operation(summary = "Complete event filter options",
            description = "Supported categories, tags, sorts, date presets, places, timezone, and limits. No result counts.")
    public FilterOptions filterOptions() {
        return filterOptions.options();
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "Event detail",
            description = "Full public event page with organizers, categories, tags, images, and every upcoming date. "
                    + "404 when the event is unknown, withdrawn, or over.")
    public EventDetail event(@PathVariable UUID id) {
        return details.event(id);
    }

    @GetMapping("/organizers/{id}")
    @Operation(summary = "Organizer detail",
            description = "Organizer profile and one page of their upcoming events (page from 0, size 1-20, default 20, same as /api/events). 404 when unknown or "
                    + "they have no discoverable event.")
    public OrganizerDetail organizer(@PathVariable UUID id,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size) {
        return details.organizer(id, BrowseCriteriaParser.page(page),
                BrowseCriteriaParser.size(size));
    }

    @GetMapping("/categories")
    @Operation(summary = "Categories", description = "Same list as filter-options.categories. Send value as category.")
    public List<FilterOption> categories() {
        return filterOptions.options().categories();
    }

    @GetMapping("/tags")
    @Operation(summary = "Tags", description = "Same list as filter-options.tags. Send value as tag.")
    public List<FilterOption> tags() {
        return filterOptions.options().tags();
    }

    @GetMapping("/cities")
    @Operation(
            summary = "Cities with published events",
            description = "Finder over cities that appear on discoverable events. "
                    + "An empty q lists cities with Amsterdam first. q matches the city slug or name.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Matching cities, at most 10.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = City.class)))),
            @ApiResponse(
                    responseCode = "400",
                    description = "q exceeds 100 characters.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<City> cities(@ParameterObject @ModelAttribute CitiesQuery query) {
        return browse.cities(parser.searchText(query.q()));
    }
}
