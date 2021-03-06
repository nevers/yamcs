import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AlgorithmDetail } from './algorithms/AlgorithmDetail';
import { ArgumentEnumDialog } from './commands/ArgumentEnumDialog';
import { CommandDetail } from './commands/CommandDetail';
import { IssueCommandDialog } from './commands/IssueCommandDialog';
import { ContainerDetail } from './containers/ContainerDetail';
import { MdbRoutingModule, routingComponents } from './MdbRoutingModule';
import { ParameterCalibration } from './parameters/ParameterCalibration';
import { ParameterDetail } from './parameters/ParameterDetail';
import { PolynomialPipe } from './pipes/PolynomialPipe';

const dialogComponents = [
  ArgumentEnumDialog,
  IssueCommandDialog,
];

const pipes = [
  PolynomialPipe,
];

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
  ],
  declarations: [
    dialogComponents,
    routingComponents,
    pipes,
    AlgorithmDetail,
    CommandDetail,
    ContainerDetail,
    ParameterCalibration,
    ParameterDetail,
  ],
  entryComponents: [
    dialogComponents,
  ],
})
export class MdbModule {
}
