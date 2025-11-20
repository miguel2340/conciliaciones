import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface UserRow { id: string; email: string; fullName: string; roles: string[]; }

@Component({
  selector: 'app-users-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  template: `
  <section class="users-admin">
    <header>
      <h3>Usuarios (solo admin)</h3>
      <div class="actions">
        <input type="text" [(ngModel)]="q" placeholder="Buscar por email o nombre" (keyup.enter)="load()" />
        <button type="button" (click)="load()">Buscar</button>
        <button type="button" (click)="newUser()">Nuevo</button>
      </div>
    </header>

    <div class="table" *ngIf="rows().length">
      <div class="thead">
        <div>Email</div><div>Nombre</div><div>Roles</div><div></div>
      </div>
      <div class="row" *ngFor="let r of rows()">
        <div>{{ r.email }}</div>
        <div>{{ r.fullName }}</div>
        <div><span class="chip" *ngFor="let ro of r.roles">{{ ro }}</span></div>
        <div class="ctl">
          <button type="button" (click)="edit(r)">Editar</button>
          <button type="button" class="danger" (click)="del(r)">Eliminar</button>
        </div>
      </div>
    </div>

    <p *ngIf="!rows().length && !loading()" class="empty">No hay usuarios que coincidan.</p>
    <p class="err" *ngIf="err()">{{ err() }}</p>

    <!-- Modal sencillo -->
    <div class="modal" *ngIf="editing()">
      <div class="box">
        <h4>{{ formId ? 'Editar' : 'Crear' }} usuario</h4>
        <form [formGroup]="form">
          <label>Email <input formControlName="email" /></label>
          <label>Nombre <input formControlName="fullName" /></label>
          <label>Contraseña <input type="password" formControlName="password" placeholder="(solo si deseas cambiar)" /></label>
          <label>Roles
            <select formControlName="roles" multiple>
              <option value="ADMIN">Administrador</option>
              <option value="USER">Profesional</option>
            </select>
          </label>
        </form>
        <div class="bar">
          <button type="button" (click)="save()" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar' }}</button>
          <button type="button" (click)="close()">Cancelar</button>
        </div>
        <p class="err" *ngIf="modalErr()">{{ modalErr() }}</p>
      </div>
    </div>
  </section>
  `,
  styles: [
    `.users-admin{display:flex;flex-direction:column;gap:.75rem}`,
    `header{display:flex;align-items:center;justify-content:space-between}`,
    `.actions{display:flex;gap:.5rem;align-items:center}`,
    `.table{display:block;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden}`,
    `.thead,.row{display:grid;grid-template-columns:2fr 2fr 1.5fr .8fr;gap:.5rem;align-items:center;padding:.5rem .75rem}`,
    `.thead{font-weight:600;background:#f8fafc;border-bottom:1px solid #e5e7eb}`,
    `.row{border-top:1px solid #f1f5f9}`,
    `.chip{display:inline-block;padding:.15rem .4rem;border-radius:999px;background:#eef2ff;color:#3730a3;margin-right:.25rem;font-size:.8rem}`,
    `.ctl{display:flex;gap:.4rem;justify-content:flex-end}`,
    `.ctl .danger{background:#ef4444;color:#fff}`,
    `.empty{color:#64748b}`,
    `.err{color:#dc2626}`,
    `.modal{position:fixed;inset:0;background:rgba(2,6,23,.55);display:flex;align-items:center;justify-content:center;z-index:1000}`,
    `.box{background:#fff;border-radius:16px;width:min(92vw,460px);padding:1rem 1.1rem;display:flex;flex-direction:column;gap:.75rem;box-shadow:0 20px 60px rgba(2,6,23,.35);border:1px solid #e5e7eb}`,
    `.box form{display:grid;gap:.65rem}`,
    `.box label{display:grid;gap:.35rem;color:#334155;font-weight:600;font-size:.9rem}`,
    `.box input, .box select{width:100%;box-sizing:border-box;padding:.65rem .8rem;border:1px solid #cbd5e1;border-radius:10px;background:#fff;outline:none;transition:border-color .2s, box-shadow .2s}`,
    `.box input:focus, .box select:focus{border-color:#60a5fa;box-shadow:0 0 0 4px rgba(96,165,250,.15)}`,
    `.box select[multiple]{min-height:110px}`,
    `.bar{display:flex;gap:.5rem;justify-content:flex-end;margin-top:.25rem}`,
    `.bar button{padding:.55rem .9rem;border-radius:10px;border:1px solid #cbd5e1;background:#fff;cursor:pointer}`,
    `.bar .danger{background:#ef4444 !important;border-color:#ef4444;color:#fff}`
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersAdminComponent implements OnInit {
  readonly rows = signal<UserRow[]>([]);
  readonly loading = signal(false);
  readonly err = signal('');
  readonly saving = signal(false);
  readonly editing = signal(false);
  readonly modalErr = signal('');
  q = '';

  form = this.fb.group({ email: [''], fullName: [''], password: [''], roles: [[] as string[]] });
  private formId: string | null = null;

  private readonly base = (() => {
    try { return `${window.location.protocol}//${window.location.hostname}:8080`; } catch { return 'http://localhost:8080'; }
  })();

  constructor(private readonly http: HttpClient, private readonly fb: FormBuilder) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true); this.err.set('');
    this.http.get<{data:UserRow[]}>(`${this.base}/api/v1/admin/users`, { params: { q: this.q } as any })
      .subscribe({ next: (res) => this.rows.set(res?.data || []), error: (e) => this.err.set(e?.error?.error || 'Error cargando usuarios'), complete: () => this.loading.set(false) });
  }

  newUser(): void { this.form.reset({ email:'', fullName:'', password:'', roles:['USER'] }); this.formId = null; this.modalErr.set(''); this.editing.set(true); }
  edit(r: UserRow): void { this.form.reset({ email:r.email, fullName:r.fullName, password:'', roles:r.roles }); this.formId = r.id; this.modalErr.set(''); this.editing.set(true); }
  close(): void { this.editing.set(false); }

  save(): void {
    this.saving.set(true); this.modalErr.set('');
    const body = { ...this.form.getRawValue() } as any;
    const req = this.formId
      ? this.http.put(`${this.base}/api/v1/admin/users/${this.formId}`, body)
      : this.http.post(`${this.base}/api/v1/admin/users`, body);
    req.subscribe({ next: () => { this.saving.set(false); this.editing.set(false); this.load(); }, error: (e) => { this.saving.set(false); this.modalErr.set(e?.error?.error || 'Error guardando'); } });
  }

  del(r: UserRow): void {
    if (!confirm(`Eliminar usuario ${r.email}?`)) return;
    this.http.delete(`${this.base}/api/v1/admin/users/${r.id}`).subscribe({ next: () => this.load(), error: (e) => this.err.set(e?.error?.error || 'Error eliminando') });
  }
}
