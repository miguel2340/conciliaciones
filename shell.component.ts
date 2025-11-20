import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

interface ShellNavItem {
  label: string;
  path: string;
  description?: string;
  comingSoon?: boolean;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShellComponent {
  readonly menuItems: ShellNavItem[] = [
    {
      label: 'Buscar Reporte por NIT',
      path: '/app/buscar-por-nit',
      description: 'Consulta y exporta radicaciones por NIT'
    },
    {
      label: 'Buscar por Multiples NITs',
      path: '/app/multiples-nit',
      description: 'Genera un TXT consolidado para una lista de NIT'
    },
    {
      label: 'Descargar por Fecha de Radicacion',
      path: '/app/por-fecha',
      description: 'Exporta radicacion3 segun filtros opcionales'
    },
    {
      label: 'Descargar Reporte por voucher',
      path: '/app/por-voucher',
      comingSoon: true
    },
    {
      label: 'Descargar Reporte Gerencial',
      path: '/app/reporte-gerencial',
      comingSoon: true
    },
    {
      label: 'Cargar Pagos',
      path: '/app/cargar-pagos',
      comingSoon: true
    },
    {
      label: 'Correccion de Pagos',
      path: '/app/correccion-pagos',
      comingSoon: true
    },
    {
      label: 'Cargar Traza Pagos',
      path: '/app/cargar-traza',
      comingSoon: true
    },
    {
      label: 'Lista de tareas',
      path: '/app/lista-tareas',
      comingSoon: true
    },
    {
      label: 'Cargar Capita',
      path: '/app/cargar-capita',
      comingSoon: true
    },
    {
      label: 'Actualizar tabla radicacion_filtrada',
      path: '/app/actualizar-tabla',
      comingSoon: true
    }
  ];

  constructor(private readonly authService: AuthService, private readonly router: Router) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }
}
