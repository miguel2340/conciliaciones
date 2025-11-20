import { Component } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: '<section class="dashboard"><h2>Bienvenido</h2><p>Has iniciado sesión correctamente.</p></section>',
  styles: [
    '.dashboard { text-align: center; display: grid; gap: 0.5rem; }',
    '.dashboard h2 { margin: 0; }'
  ]
})
export class DashboardComponent {}
