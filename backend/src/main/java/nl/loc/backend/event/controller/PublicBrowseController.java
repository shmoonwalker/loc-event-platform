package nl.loc.backend.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.request.CitiesQuery;
import nl.loc.backend.event.dto.request.EventsQuery;
import nl.loc.backend.event.dto.request.HomeQuery;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.dto.response.HomeView;
import nl.loc.backend.event.service.EventBrowseService;
import nl.loc.backend.event.service.HomeService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public PublicBrowseController(HomeService homeService, EventBrowseService browse, BrowseCriteriaParser parser) {
        this.homeService = homeService;
        this.browse = browse;
        this.parser = parser;
    }

    @GetMapping("/home")
    @Operation(
            summary = "Homepage",
            description = "Returns tags, category counts, and four rails. Each rail previews 6 events with total, "
                    + "items, and hasMore. Query city applies only to nearYou and defaults to amsterdam. "
                    + "Tags, categories, tonight, and this weekend are physical discovery and are not filtered "
                    + "by that city. Online is location.type ONLINE and is not filtered by city. No GPS. "
                    + "Tags are a field, not a rail. Near-you see-all is "
                    + "GET /api/events?city={slug}&when=upcoming&place=physical with page 0 and size at most 20.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Homepage. city scopes the near-you rail only.",
                    content = @Content(schema = @Schema(implementation = HomeView.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "city is not a slug such as amsterdam.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public HomeView home(@ParameterObject @ModelAttribute HomeQuery query) {
        return homeService.home(parser.homeCity(query.getCity()));
    }

    @GetMapping("/events")
    @Operation(
            summary = "Events collection",
            description = "Search, filter, and page published events. See-all uses this collection with page 0 and "
                    + "size at most 20. Near-you see-all passes city, when=upcoming, and place=physical. "
                    + "Other homepage rails omit city. page starts at 0. Repeated category is OR. Repeated tag is AND. "
                    + "q is matched with websearch_to_tsquery on title, description, venue, city, categories, and tags. "
                    + "Sort defaults to relevance when q is present, otherwise start time, then id. "
                    + "Unknown place, category, tag, when, or sort returns 400.")
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
        return browse.cities(parser.searchText(query.getQ()));
    }
}
