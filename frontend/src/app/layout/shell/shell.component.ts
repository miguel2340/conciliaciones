import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

interface ShellNavItem {
  label: string;
  path: string;
  description?: string;
  comingSoon?: boolean;
  adminOnly?: boolean;
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
    { label: 'Buscar Reporte por NIT', path: '/app/buscar-por-nit', description: 'Consulta y exporta radicaciones por NIT' },
    { label: 'Buscar por Multiples NITs', path: '/app/multiples-nit', description: 'Genera un TXT consolidado para una lista de NIT' },
    { label: 'Descargar por Fecha de Radicacion', path: '/app/por-fecha', description: 'Exporta radicacion3 segun filtros opcionales' },
    { label: 'Descarga de reportes', path: '/app/reportes', description: 'Voucher y gerencial' },
    { label: 'Carga de planos', path: '/app/carga', description: 'Pagos, cápita, correcciones y traza' },
    { label: 'Lista de tareas', path: '/app/lista-tareas' },
    { label: 'Productividad', path: '/app/productividad', description: 'Usuarios y cargas por fecha/voucher' },
    { label: 'Conciliación', path: '/app/conciliacion', description: 'CSV con columnas calculadas' },
    { label: 'Usuarios (Admin)', path: '/app/admin/usuarios', description: 'Gestión de usuarios', adminOnly: true },
    { label: 'Actualizar tabla radicacion_filtrada', path: '/app/actualizar-tabla', description: 'Reemplaza radicacion_filtrada', adminOnly: true }
  ];

  constructor(private readonly authService: AuthService, private readonly router: Router) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }
}
