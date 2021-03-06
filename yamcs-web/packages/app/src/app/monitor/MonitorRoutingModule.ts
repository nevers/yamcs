import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { AlarmsPage } from './alarms/AlarmsPage';
import { DisplayFilePage } from './displays/DisplayFilePage';
import { DisplayFilePageDirtyGuard } from './displays/DisplayFilePageDirtyGuard';
import { DisplayFolderPage } from './displays/DisplayFolderPage';
import { DisplayPage } from './displays/DisplayPage';
import { DisplaysPage } from './displays/DisplaysPage';
import { EventsPage } from './events/EventsPage';
import { ExtensionPage } from './ext/ExtensionPage';
import { LayoutPage } from './layouts/LayoutPage';
import { LayoutsPage } from './layouts/LayoutsPage';
import { ParameterChartTab } from './parameters/ParameterChartTab';
import { ParameterDataTab } from './parameters/ParameterDataTab';
import { ParameterPage } from './parameters/ParameterPage';
import { ParametersPage } from './parameters/ParametersPage';
import { ParameterSummaryTab } from './parameters/ParameterSummaryTab';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',  // See DisplaysPage.ts for documentation
    component: InstancePage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'home',
      },
      {
        path: 'alarms',
        component: AlarmsPage,
      },
      {
        path: 'displays',
        pathMatch: 'full',
        redirectTo: 'displays/browse'
      },
      {
        path: 'displays/browse',
        component: DisplaysPage,
        children: [
          {
            path: '**',
            component: DisplayFolderPage,
          }
        ]
      },
      {
        path: 'displays/files',
        component: DisplayPage,
        children: [
          {
            path: '**',
            component: DisplayFilePage,
            canDeactivate: [DisplayFilePageDirtyGuard],
          }
        ]
      }, {
        path: 'events',
        component: EventsPage,
        canActivate: [MayReadEventsGuard],
      }, {
        path: 'layouts',
        pathMatch: 'full',
        component: LayoutsPage,
      }, {
        path: 'layouts/:name',
        component: LayoutPage,
      }, {
        path: 'parameters',
        pathMatch: 'full',
        component: ParametersPage,
      }, {
        path: 'parameters/:qualifiedName',
        component: ParameterPage,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'summary'
          }, {
            path: 'summary',
            component: ParameterSummaryTab,
          }, {
            path: 'chart',
            component: ParameterChartTab,
          }, {
            path: 'data',
            component: ParameterDataTab,
          }
        ]
      }, {
        path: 'ext/:name',
        component: ExtensionPage,
      }
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
  providers: [
    DisplayFilePageDirtyGuard,
  ]
})
export class MonitorRoutingModule { }

export const routingComponents = [
  AlarmsPage,
  DisplaysPage,
  DisplayFilePage,
  DisplayFolderPage,
  DisplayPage,
  EventsPage,
  ExtensionPage,
  LayoutsPage,
  LayoutPage,
  ParametersPage,
  ParameterPage,
  ParameterDataTab,
  ParameterChartTab,
  ParameterSummaryTab,
];
