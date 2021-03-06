package br.com.openpdv.controlador.comandos;

import br.com.openpdv.controlador.core.CoreService;
import br.com.phdss.Util;
import br.com.openpdv.modelo.core.Dados;
import br.com.openpdv.modelo.core.OpenPdvException;
import br.com.openpdv.modelo.core.filtro.ECompara;
import br.com.openpdv.modelo.core.filtro.Filtro;
import br.com.openpdv.modelo.core.filtro.FiltroBinario;
import br.com.openpdv.modelo.core.filtro.FiltroData;
import br.com.openpdv.modelo.core.filtro.FiltroGrupo;
import br.com.openpdv.modelo.ecf.EcfNota;
import br.com.openpdv.modelo.ecf.EcfNotaEletronica;
import br.com.openpdv.modelo.ecf.EcfVenda;
import br.com.openpdv.modelo.ecf.EcfZ;
import br.com.openpdv.modelo.sistema.SisCliente;
import br.com.openpdv.visao.core.Caixa;
import br.com.phdss.controlador.PAF;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.swing.JOptionPane;
import org.apache.log4j.Logger;

/**
 * Classe que realiza a acao de enviar os dados ao servidor.
 *
 * @author Pedro H. Lira
 */
public abstract class ComandoEnviarDados implements IComando {

    protected CoreService service;
    protected Logger log;
    protected Date inicio;
    protected Date fim;
    protected StringBuilder erros;

    public ComandoEnviarDados() {
        this(Util.getData(PAF.AUXILIAR.getProperty("out.envio", null)), null);
    }

    public ComandoEnviarDados(Date inicio, Date fim) {
        this.service = new CoreService();
        this.log = Logger.getLogger(ComandoEnviarDados.class);
        this.inicio = inicio != null ? inicio : new Date();
        if (fim != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(fim);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            this.fim = cal.getTime();
        }
        this.erros = new StringBuilder();
    }

    /**
     * Metodo estatico para criar uma instancia de acordo com o config.
     *
     * @return o objeto que envia dados.
     */
    public static ComandoEnviarDados getInstancia() {
        ComandoEnviarDados ced = Util.getConfig().getProperty("sinc.tipo").equals("rest") ? new ComandoEnviarDadosRemoto() : new ComandoEnviarDadosLocal();
        return ced;
    }

    /**
     * Metodo estatico para criar uma instancia de acordo com o config.
     *
     * @param inicio a data de inicio.
     * @param fim a data de fim.
     * @return o objeto que envia dados.
     */
    public static ComandoEnviarDados getInstancia(Date inicio, Date fim) {
        ComandoEnviarDados ced = Util.getConfig().getProperty("sinc.tipo").equals("rest") ? new ComandoEnviarDadosRemoto(inicio, fim) : new ComandoEnviarDadosLocal(inicio, fim);
        return ced;
    }

    @Override
    public void executar() throws OpenPdvException {
        if (Util.getConfig().getProperty("sinc.nota").equals("true")) {
            // enviando as notas
            notas();
        }
        if (Util.getConfig().getProperty("sinc.nfe").equals("true")) {
            // enviando as nfes
            nfes();
        }
        if (Util.getConfig().getProperty("sinc.cliente").equals("true")) {
            // enviando os clientes nao sincronizados
            clientes();
        }
        if (Util.getConfig().getProperty("sinc.venda").equals("true")) {
            // enviando as vendas nao sincronizadas
            vendas();
        }
        if (Util.getConfig().getProperty("sinc.reducaoZ").equals("true")) {
            // enviando as Z
            zs();
        }

        // se sucesso atualiza no arquivo a data do ultimo envio
        if (erros.length() == 0) {
            salvar();
        } else {
            int escolha = JOptionPane.showOptionDialog(Caixa.getInstancia(), "Ocorreu algum problema no sincronismo.\nDeseja ignorar este erro?", "Sincronismo",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, Util.OPCOES, JOptionPane.YES_OPTION);
            if (escolha == JOptionPane.YES_OPTION) {
                salvar();
            } else {
                erros.append("Verifique o log do sistema.");
                throw new OpenPdvException(erros.toString());
            }
        }
    }

    @Override
    public void desfazer() throws OpenPdvException {
        // comando nao aplicavel.
    }

    /**
     * Metodo que envia o objeto ao servidor ou local.
     *
     * @param <E> o tipo de classe a ser enviada.
     * @param tipo o nome da url/arquivo do objeto.
     * @param lista a lista de objetos a serem enviadas.
     * @return uma lista com os objetos que foram sincronizados.
     * @exception Exception dispara caso ocorra uma excecao.
     */
    protected abstract <E extends Dados> List<E> enviar(String tipo, List<E> lista) throws Exception;

    /**
     * Metodo que envia as notas.
     */
    private void notas() {
        try {
            FiltroGrupo filtro = new FiltroGrupo();
            FiltroData fd = new FiltroData("ecfNotaData", ECompara.MAIOR_IGUAL, inicio);
            filtro.add(fd, Filtro.E);
            if (fim != null) {
                FiltroData fd1 = new FiltroData("ecfNotaData", ECompara.MENOR, fim);
                filtro.add(fd1, Filtro.E);
            }
            List<EcfNota> notas = service.selecionar(new EcfNota(), 0, 0, filtro);
            if (!notas.isEmpty()) {
                enviar("nota", notas);
                log.info("Notas enviadas = " + notas.size());
            }
        } catch (Exception ex) {
            erros.append("Erro no envio de algumas Notas.\n");
            log.error("Erro no envio de algumas Notas.", ex);
        }
    }

    /**
     * Metodo que envia as nfes.
     */
    private void nfes() {
        try {
            FiltroGrupo filtro = new FiltroGrupo();
            FiltroData fd = new FiltroData("ecfNotaEletronicaData", ECompara.MAIOR_IGUAL, inicio);
            filtro.add(fd, Filtro.E);
            if (fim != null) {
                FiltroData fd1 = new FiltroData("ecfNotaEletronicaData", ECompara.MENOR, fim);
                filtro.add(fd1, Filtro.E);
            }
            List<EcfNotaEletronica> nfes = service.selecionar(new EcfNotaEletronica(), 0, 0, filtro);
            if (!nfes.isEmpty()) {
                enviar("nfe", nfes);
                log.info("NFes enviadas = " + nfes.size());
            }
        } catch (Exception ex) {
            erros.append("Erro no envio de algumas NFEs.\n");
            log.error("Erro no envio de algumas NFEs.", ex);
        }
    }

    /**
     * Metodo que envia os clientes.
     */
    private void clientes() {
        try {
            FiltroBinario fb = new FiltroBinario("sisClienteSinc", ECompara.IGUAL, false);
            List<SisCliente> clientes = service.selecionar(new SisCliente(), 0, 0, fb);
            if (!clientes.isEmpty()) {
                clientes = enviar("cliente", clientes);
                for (SisCliente cliente : clientes) {
                    // marca a venda como sincronizada
                    cliente.setSisClienteSinc(true);
                    service.salvar(cliente);
                }
                log.info("Clientes enviados = " + clientes.size());
            }
        } catch (Exception ex) {
            erros.append("Erro no envio de alguns clientes.\n");
            log.error("Erro no envio de alguns clientes.", ex);
        }
    }

    /**
     * Metodo que envia as vendas.
     */
    private void vendas() {
        try {
            FiltroBinario fb = new FiltroBinario("ecfVendaSinc", ECompara.IGUAL, false);
            List<EcfVenda> vendas = service.selecionar(new EcfVenda(), 0, 0, fb);
            if (!vendas.isEmpty()) {
                vendas = enviar("venda", vendas);
                for (EcfVenda venda : vendas) {
                    // marca a venda como sincronizada
                    venda.setEcfVendaSinc(true);
                    service.salvar(venda);
                }
                log.info("Vendas enviadas = " + vendas.size());
            }
        } catch (Exception ex) {
            erros.append("Erro no envio de algumas vendas.\n");
            log.error("Erro no envio de algumas vendas.", ex);
        }
    }

    /**
     * Metodo que envia as Zs.
     */
    private void zs() {
        try {
            FiltroGrupo filtro = new FiltroGrupo();
            FiltroData fd = new FiltroData("ecfZMovimento", ECompara.MAIOR_IGUAL, inicio);
            filtro.add(fd, Filtro.E);
            if (fim != null) {
                FiltroData fd1 = new FiltroData("ecfZMovimento", ECompara.MENOR, fim);
                filtro.add(fd1, Filtro.E);
            }
            List<EcfZ> zs = service.selecionar(new EcfZ(), 0, 0, filtro);
            if (!zs.isEmpty()) {
                enviar("reducaoZ", zs);
                log.info("Zs enviadas = " + zs.size());
            }
        } catch (Exception ex) {
            erros.append("Erro no envio de algumas Zs.\n");
            log.error("Erro no envio de algumas Zs.", ex);
        }
    }

    /**
     * Metodo que salva a data de encio no arquivo.
     *
     * @throws OpenPdvException caso ocorra algum erro.
     */
    private void salvar() throws OpenPdvException {
        try {
            PAF.AUXILIAR.setProperty("out.envio", Util.getData(new Date()));
            Util.criptografar(null, PAF.AUXILIAR);
        } catch (Exception ex) {
            throw new OpenPdvException("Erro ao salvar no arquivo auxiliar.\nVerifique o log do sistema.", ex);
        }
    }
}
