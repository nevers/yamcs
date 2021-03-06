import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Alarm, Instance } from '@yamcs/client';

@Component({
  selector: 'app-alarm-detail',
  templateUrl: './AlarmDetail.html',
  styleUrls: ['./AlarmDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmDetail {

  @Input()
  alarm: Alarm;

  @Input()
  instance: Instance;
}
