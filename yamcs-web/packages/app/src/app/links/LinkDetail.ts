import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Link } from '@yamcs/client';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  selector: 'app-link-detail',
  templateUrl: './LinkDetail.html',
  styleUrls: ['./LinkDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkDetail {

  @Input()
  link: Link;

  constructor(private authService: AuthService, private yamcs: YamcsService) { }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  enableLink() {
    this.yamcs.getInstanceClient()!.enableLink(this.link.name);
  }

  disableLink() {
    this.yamcs.getInstanceClient()!.disableLink(this.link.name);
  }
}
