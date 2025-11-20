import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { ShellComponent } from './layout/shell/shell.component';
import { NitSearchComponent } from './features/radicacion/nit-search/nit-search.component';
import { ReportesHubComponent } from './features/reportes/hub/reportes-hub.component';
import { CargaHubComponent } from './features/carga/hub/carga-hub.component';
import { MultiNitExportComponent } from './features/radicacion/multi-nit-export/multi-nit-export.component';
import { VoucherReportComponent } from './features/reportes/voucher/voucher-report.component';
import { FechaExportComponent } from './features/radicacion/fecha-export/fecha-export.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';

export const appRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'auth/login' },
  {
    path: 'auth',
    children: [{ path: 'login', component: LoginComponent }]
  },
  {
    path: 'app',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'buscar-por-nit' },
      { path: 'buscar-por-nit', component: NitSearchComponent },
      { path: 'multiples-nit', component: MultiNitExportComponent },
      { path: 'por-fecha', component: FechaExportComponent },
      { path: 'por-voucher', loadComponent: () => import('./features/reportes/voucher/voucher-report.component').then(m => m.VoucherReportComponent) },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'reportes', component: ReportesHubComponent },
      { path: 'carga', component: CargaHubComponent },
      { path: 'carga/pagos', loadComponent: () => import('./features/carga/pagos/carga-pagos.component').then(m => m.CargaPagosComponent) },
      { path: 'carga/pagos-api', loadComponent: () => import('./features/carga/pagos-api/pagos-api.component').then(m => m.PagosApiComponent) },
      { path: 'carga/pagos-capita', loadComponent: () => import('./features/carga/pagos-capita/carga-pagos-capita.component').then(m => m.CargaPagosCapitaComponent) },
      { path: 'carga/correcciones-pagos', loadComponent: () => import('./features/carga/correcciones-pagos/carga-correcciones-pagos.component').then(m => m.CargaCorreccionesPagosComponent) },
      { path: 'carga/correcciones-pagos-capita', loadComponent: () => import('./features/carga/correcciones-pagos-capita/carga-correcciones-pagos-capita.component').then(m => m.CargaCorreccionesPagosCapitaComponent) },
      { path: 'carga/pagos-traza', loadComponent: () => import('./features/carga/pagos-traza/carga-pagos-traza.component').then(m => m.CargaPagosTrazaComponent) },
      { path: 'carga/radicacion-capita', loadComponent: () => import('./features/carga/radicacion-capita-view/radicacion-capita-view.component').then(m => m.RadicacionCapitaViewComponent) },
      { path: 'lista-tareas', loadComponent: () => import('./features/lista-tareas/lista-tareas.component').then(m => m.ListaTareasComponent) },
      { path: 'productividad', loadComponent: () => import('./features/productividad/productividad.component').then(m => m.ProductividadComponent) },
      { path: 'conciliacion', loadComponent: () => import('./features/conciliacion/conciliacion.component').then(m => m.ConciliacionComponent) },
      { path: 'admin/usuarios', canActivate: [() => import('./core/auth/admin.guard').then(m => m.adminGuard)], loadComponent: () => import('./features/admin/users/users-admin.component').then(m => m.UsersAdminComponent) },
      { path: 'actualizar-tabla', canActivate: [() => import('./core/auth/admin.guard').then(m => m.adminGuard)], loadComponent: () => import('./features/admin/actualizar-filtrada/actualizar-filtrada.component').then(m => m.ActualizarFiltradaComponent) },
      { path: 'reportes', component: ReportesHubComponent },
      { path: '**', redirectTo: 'buscar-por-nit' }
    ]
  },
  { path: '**', redirectTo: 'app' }
];
