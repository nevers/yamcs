import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { GeneralInfo, Parameter } from '@yamcs/client';
import { AuthService } from '../core/services/AuthService';
import { Synchronizer } from '../core/services/Synchronizer';
import { YamcsService } from '../core/services/YamcsService';
import { DyDataSource } from '../shared/widgets/DyDataSource';


@Component({
  templateUrl: './SystemPage.html',
  styleUrls: ['./SystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPage implements OnDestroy {

  info$: Promise<GeneralInfo>;

  jvmMemoryUsedParameter$: Promise<Parameter | null>;
  jvmTotalMemoryParameter$: Promise<Parameter | null>;
  jvmThreadCountParameter$: Promise<Parameter | null>;

  jvmMemoryUsedDataSource: DyDataSource;
  jvmThreadCountDataSource: DyDataSource;

  constructor(yamcs: YamcsService, title: Title, private authService: AuthService, synchronizer: Synchronizer) {
    title.setTitle('System');
    this.info$ = yamcs.yamcsClient.getGeneralInfo();

    this.jvmMemoryUsedParameter$ = this.info$.then(info => {
       return yamcs.getInstanceClient()!.getParameter(`/yamcs/${info.serverId}/jvmMemoryUsed`);
    });
    this.jvmTotalMemoryParameter$ = this.info$.then(info => {
      return yamcs.getInstanceClient()!.getParameter(`/yamcs/${info.serverId}/jvmTotalMemory`);
    });
    this.jvmThreadCountParameter$ = this.info$.then(info => {
      return yamcs.getInstanceClient()!.getParameter(`/yamcs/${info.serverId}/jvmThreadCount`);
    });

    Promise.all([this.jvmMemoryUsedParameter$, this.jvmTotalMemoryParameter$]).then(results => {
      if (results[0] && results[1]) {
        this.jvmMemoryUsedDataSource = new DyDataSource(yamcs, synchronizer);
        this.jvmMemoryUsedDataSource.addParameter(results[0]!, results[1]!);
      }
    });

    this.jvmThreadCountParameter$.then(parameter => {
      if (parameter) {
        this.jvmThreadCountDataSource = new DyDataSource(yamcs, synchronizer);
        this.jvmThreadCountDataSource.addParameter(parameter);
      }
    });
  }

  hasParameterPrivilege(parameter: string) {
    return this.authService.getUser()!.hasObjectPrivilege('ReadParameter', parameter);
  }

  ngOnDestroy() {
    if (this.jvmMemoryUsedDataSource) {
      this.jvmMemoryUsedDataSource.disconnect();
    }
    if (this.jvmThreadCountDataSource) {
      this.jvmThreadCountDataSource.disconnect();
    }
  }
}
