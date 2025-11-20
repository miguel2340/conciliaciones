package com.pagosyradicacion.backend.radicacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import org.hibernate.annotations.Formula;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "radicacion_filtrada")
public class RadicacionRegistro {

  @Id
  private Long id;

  @Column(name = "modalidad_pago")
  private String modalidadPago;

  @Column(name = "modalidad_factura")
  private String modalidadfactura;

  @Column(name = "nit")
  private String nit;

  @Column(name = "nom_prestador")
  private String nomPrestador;

  @Column(name = "tipo_red")
  private String tipoRed;

  @Column(name = "departamento")
  private String departamento;

  @Column(name = "municipio")
  private String municipio;

  @Column(name = "prefijo")
  private String prefijo;

  @Column(name = "factura")
  private String factura;

  @Column(name = "prefijo_factura")
  private String prefijoFactura;

  @Column(name = "mes_radicacion")
  private String mesRadicacion;

  @Column(name = "estado_aplicacion")
  private String estadoAplicacion;

  @Column(name = "fecha_radicacion")
  private LocalDateTime fechaRadicacion;

  @Column(name = "rango_dias")
  private String rangoDias;

  @Column(name = "fecha_factura")
  private LocalDateTime fechaFactura;

  @Column(name = "valor_factura")
  private BigDecimal valorFactura;

  @Column(name = "valor_iva")
  private BigDecimal valorIva;

  @Column(name = "nota_credito")
  private BigDecimal notaCredito;

  @Column(name = "valor_glosa_inicial")
  private BigDecimal valorGlosaInicial;

  @Column(name = "valor_no_glosado_inicial")
  private BigDecimal valorNoGlosadoInicial;

  @Column(name = "valor_aceptado_primera_respuesta")
  private BigDecimal valorAceptadoPrimeraRespuesta;

  @Column(name = "valor_levantado_primera_respuesta")
  private BigDecimal valorLevantadoPrimeraRespuesta;

  @Column(name = "valor_ratificado_primera_respuesta")
  private BigDecimal valorRatificadoPrimeraRespuesta;

  @Column(name = "valor_aceptado_segunda_respuesta")
  private BigDecimal valorAceptadoSegundaRespuesta;

  @Column(name = "valor_levantado_segunda_respuesta")
  private BigDecimal valorLevantadoSegundaRespuesta;

  @Column(name = "valor_ratificado_segunda_respuesta")
  private BigDecimal valorRatificadoSegundaRespuesta;

  @Column(name = "valor_aceptado_conciliacion")
  private BigDecimal valorAceptadoConciliacion;

  @Column(name = "valor_levantado_conciliacion")
  private BigDecimal valorLevantadoConciliacion;

  @Column(name = "valor_ratificado_conciliacion")
  private BigDecimal valorRatificadoConciliacion;

  @Column(name = "Valor_actual_aceptado")
  private BigDecimal valorActualAceptado;

  @Column(name = "valor_actual_reconocido")
  private BigDecimal valorActualReconocido;

  @Column(name = "valor_final_ratificado")
  private BigDecimal valorFinalRatificado;

  @Column(name = "valor_pagado")
  private BigDecimal valorPagado;

  @Column(name = "porcentaje_pago")
  private BigDecimal porcentajePago;

  @Column(name = "estado")
  private String estado;

  @Column(name = "voucher")
  private String voucher;

  @Column(name = "feccha_pago")
  private LocalDateTime fechaPago;

  @Column(name = "id_fomag")
  private String idFomag;

  @Formula("CAST(NULL AS varchar(255))")
  private String nitNorm;

  @Formula("CAST(NULL AS varchar(255))")
  private String voucherNorm;

  protected RadicacionRegistro() {}

  public Long getId() {
    return id;
  }

  public String getModalidadPago() {
    return modalidadPago;
  }

  public String getNit() {
    return nit;
  }

  public String getNomPrestador() {
    return nomPrestador;
  }

  public String getTipoRed() {
    return tipoRed;
  }

  public String getDepartamento() {
    return departamento;
  }

  public String getMunicipio() {
    return municipio;
  }

  public String getPrefijo() {
    return prefijo;
  }

  public String getFactura() {
    return factura;
  }

  public String getPrefijoFactura() {
    return prefijoFactura;
  }

  public String getMesRadicacion() {
    return mesRadicacion;
  }

  public String getEstadoAplicacion() {
    return estadoAplicacion;
  }

  public LocalDateTime getFechaRadicacion() {
    return fechaRadicacion;
  }

  public String getRangoDias() {
    return rangoDias;
  }

  public LocalDateTime getFechaFactura() {
    return fechaFactura;
  }

  public BigDecimal getValorFactura() {
    return valorFactura;
  }

  public BigDecimal getValorIva() {
    return valorIva;
  }

  public BigDecimal getNotaCredito() {
    return notaCredito;
  }

  public BigDecimal getValorGlosaInicial() {
    return valorGlosaInicial;
  }

  public BigDecimal getValorNoGlosadoInicial() {
    return valorNoGlosadoInicial;
  }

  public BigDecimal getValorAceptadoPrimeraRespuesta() {
    return valorAceptadoPrimeraRespuesta;
  }

  public BigDecimal getValorLevantadoPrimeraRespuesta() {
    return valorLevantadoPrimeraRespuesta;
  }

  public BigDecimal getValorRatificadoPrimeraRespuesta() {
    return valorRatificadoPrimeraRespuesta;
  }

  public BigDecimal getValorAceptadoSegundaRespuesta() {
    return valorAceptadoSegundaRespuesta;
  }

  public BigDecimal getValorLevantadoSegundaRespuesta() {
    return valorLevantadoSegundaRespuesta;
  }

  public BigDecimal getValorRatificadoSegundaRespuesta() {
    return valorRatificadoSegundaRespuesta;
  }

  public BigDecimal getValorAceptadoConciliacion() {
    return valorAceptadoConciliacion;
  }

  public BigDecimal getValorLevantadoConciliacion() {
    return valorLevantadoConciliacion;
  }

  public BigDecimal getValorRatificadoConciliacion() {
    return valorRatificadoConciliacion;
  }

  public BigDecimal getValorActualAceptado() {
    return valorActualAceptado;
  }

  public BigDecimal getValorActualReconocido() {
    return valorActualReconocido;
  }

  public BigDecimal getValorFinalRatificado() {
    return valorFinalRatificado;
  }

  public BigDecimal getValorPagado() {
    return valorPagado;
  }

  public BigDecimal getPorcentajePago() {
    return porcentajePago;
  }

  public String getEstado() {
    return estado;
  }

  public String getVoucher() {
    return voucher;
  }

  public LocalDateTime getFechaPago() {
    return fechaPago;
  }

  public String getIdFomag() {
    return idFomag;
  }

  public String getNitNorm() {
    return nitNorm;
  }

  public String getVoucherNorm() {
    return voucherNorm;
  }
}
