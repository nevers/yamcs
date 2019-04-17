import { ChangeDetectionStrategy, Component, HostBinding, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { ConnectionInfo } from '@yamcs/client';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService } from '../../core/services/ConfigService';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { SelectInstanceDialog } from '../../shared/dialogs/SelectInstanceDialog';
import { User } from '../../shared/User';


@Component({
  selector: 'app-root',
  templateUrl: './AppComponent.html',
  styleUrls: ['./AppComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent implements OnDestroy {

  @HostBinding('class')
  componentCssClass: string;

  title = 'Yamcs';
  tag: string;

  connectionInfo$: Observable<ConnectionInfo | null>;
  user$: Observable<User | null>;

  sidebar$: Observable<boolean>;
  darkMode$: Observable<boolean>;
  showMdbItem$ = new BehaviorSubject<boolean>(false);
  showMenuToggle$: Observable<boolean>;

  userSubscription: Subscription;

  constructor(
    yamcs: YamcsService,
    private router: Router,
    route: ActivatedRoute,
    private authService: AuthService,
    private preferenceStore: PreferenceStore,
    private dialog: MatDialog,
    configService: ConfigService,
  ) {
    this.tag = configService.getTag();
    this.connectionInfo$ = yamcs.connectionInfo$;
    this.user$ = authService.user$;

    this.userSubscription = this.user$.subscribe(user => {
      if (user) {
        this.showMdbItem$.next(user.hasSystemPrivilege('GetMissionDatabase'));
      } else {
        this.showMdbItem$.next(false);
      }
    });

    this.sidebar$ = preferenceStore.sidebar$;

    this.darkMode$ = preferenceStore.darkMode$;
    if (preferenceStore.isDarkMode()) {
      this.enableDarkMode();
    }

    this.showMenuToggle$ = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd),
      map(evt => {
        let child = route;
        while (child.firstChild) {
          child = child.firstChild;
        }

        if (child.snapshot.data && child.snapshot.data['hasSidebar'] === false) {
          return false;
        } else {
          return true;
        }
      }),
    );
  }

  openInstanceDialog() {
    this.dialog.open(SelectInstanceDialog, {
      width: '600px',
      autoFocus: false,
    });
  }

  toggleDarkTheme() {
    if (this.preferenceStore.isDarkMode()) {
      this.disableDarkMode();
    } else {
      this.enableDarkMode();
    }
  }

  toggleSidebar() {
    if (this.preferenceStore.showSidebar()) {
      this.preferenceStore.setShowSidebar(false);
    } else {
      this.preferenceStore.setShowSidebar(true);
    }
  }

  logout() {
    this.authService.logout(true);
  }

  private enableDarkMode() {
    document.body.classList.add('dark-theme');
    this.preferenceStore.setDarkMode(true);
  }

  private disableDarkMode() {
    document.body.classList.remove('dark-theme');
    this.preferenceStore.setDarkMode(false);
  }

  ngOnDestroy() {
    if (this.userSubscription) {
      this.userSubscription.unsubscribe();
    }
  }
}
