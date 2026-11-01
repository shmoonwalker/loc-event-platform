import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FollowedOrganizersApi } from '../../core/followed-organizers-api';
import { valueOf } from '../../core/resource';
import { ErrorState } from '../../shared/error-state/error-state';
import { FollowButton } from '../../shared/follow-button/follow-button';

/**
 * The "Following" tab: organizers the member follows, most recent first. A card opens the organizer's page
 * with their upcoming events. Unfollowing keeps the card until the next load, so a mis-click can be undone.
 */
@Component({
  selector: 'app-followed-organizers',
  imports: [RouterLink, ErrorState, FollowButton],
  templateUrl: './followed-organizers.html',
})
export class FollowedOrganizers {
  private readonly api = inject(FollowedOrganizersApi);

  protected readonly followed = rxResource({ stream: () => this.api.list() });
  protected readonly organizers = computed(() => valueOf(this.followed));
  protected readonly skeletons = Array.from({ length: 6 });
}
