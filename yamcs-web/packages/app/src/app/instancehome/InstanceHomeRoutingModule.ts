import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { InstanceHomePage } from './InstanceHomePage';

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
        component: InstanceHomePage,
      }
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class InstanceHomeRoutingModule { }

export const routingComponents = [
  InstanceHomePage,
];
