import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-carga-hub',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './carga-hub.component.html',
  styleUrl: './carga-hub.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CargaHubComponent {
  constructor(private readonly router: Router) {}

  abrir(path: string): void {
    this.router.navigate(['/app/carga', path]);
  }
}
