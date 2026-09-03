package org.vinni.servidor.gui;


import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Author: Vinni
 *
 */
public class PrincipalSrv extends javax.swing.JFrame {
    private final int PORT = 12345;
    private ServerSocket serverSocket;


    // Bandera para distinguir un cierre intencional (botón Detener) de un
    // error real de red al leer del ServerSocket.
    private volatile boolean detenerSolicitado = false;


    // Lista concurrente de clientes conectados (segura para acceso desde
    // varios hilos: el hilo que acepta conexiones y los hilos de cada cliente).
    private final List<ClientHandler> clientes = new CopyOnWriteArrayList<>();
    private final AtomicInteger contadorClientes = new AtomicInteger(0);


    /**
     * Creates new form Principal1
     */
    public PrincipalSrv() {
        initComponents();
    }


    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">
    private void initComponents() {
        this.setTitle("Servidor ...");


        bIniciar = new javax.swing.JButton();
        bDetener = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        mensajesTxt = new JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();


        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);


        bIniciar.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        bIniciar.setText("INICIAR SERVIDOR");
        bIniciar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bIniciarActionPerformed(evt);
            }
        });
        getContentPane().add(bIniciar);
        bIniciar.setBounds(100, 90, 250, 40);


        bDetener.setFont(new java.awt.Font("Segoe UI", 0, 18));
        bDetener.setText("DETENER SERVIDOR");
        bDetener.setEnabled(false);
        bDetener.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bDetenerActionPerformed(evt);
            }
        });
        getContentPane().add(bDetener);
        bDetener.setBounds(100, 130, 250, 40);


        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(204, 0, 0));
        jLabel1.setText("SERVIDOR TCP : HOEL");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(150, 10, 160, 17);


        mensajesTxt.setColumns(25);
        mensajesTxt.setRows(5);


        jScrollPane1.setViewportView(mensajesTxt);


        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 210, 410, 70);


        setSize(new java.awt.Dimension(491, 340));
        setLocationRelativeTo(null);
    }// </editor-fold>


    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new PrincipalSrv().setVisible(true);
            }
        });
    }


    private void bIniciarActionPerformed(java.awt.event.ActionEvent evt) {
        iniciarServidor();
    }


    private void bDetenerActionPerformed(java.awt.event.ActionEvent evt) {
        detenerServidor();
    }


    private void iniciarServidor() {
        JOptionPane.showMessageDialog(this, "Iniciando servidor");
        detenerSolicitado = false;


        bIniciar.setEnabled(false);
        bDetener.setEnabled(true);


        new Thread(new Runnable() {
            public void run() {
                try {
                    InetAddress addr = InetAddress.getLocalHost();
                    serverSocket = new ServerSocket(PORT);
                    log("Servidor TCP en ejecución: " + addr + " ,Puerto " + serverSocket.getLocalPort());
                    mostrarIpsLocales();


                    while (true) {
                        // Espera y acepta nuevas conexiones de forma indefinida;
                        // cada conexión aceptada se delega a un hilo propio,
                        // por lo que el servidor puede seguir aceptando más
                        // clientes (locales o remotos) mientras ya atiende a otros.
                        Socket socket = serverSocket.accept();
                        int numero = contadorClientes.incrementAndGet();
                        String id = "Cliente-" + numero + " (" + socket.getInetAddress().getHostAddress() + ")";


                        ClientHandler handler = new ClientHandler(socket, id);
                        clientes.add(handler);
                        log(id + " se ha conectado. Clientes conectados: " + clientes.size());


                        new Thread(handler).start();
                    }
                } catch (IOException ex) {
                    // Si el cierre fue solicitado por el botón Detener, no es un error real
                    if (detenerSolicitado) {
                        log("Servidor detenido correctamente.");
                    } else {
                        log("Error en el servidor: " + ex.getMessage());
                    }
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        bIniciar.setEnabled(true);
                        bDetener.setEnabled(false);
                    });
                }
            }
        }).start();
    }


    /**
     * Detiene el servidor: cierra el ServerSocket (esto desbloquea el
     * accept() y termina el hilo de escucha) y desconecta a todos los
     * clientes conectados, pero NO cierra la ventana.
     */
    private void detenerServidor() {
        detenerSolicitado = true;
        try {
            // 1. Cerrar conexiones de todos los clientes
            for (ClientHandler c : clientes) {
                c.cerrar();
            }
            clientes.clear();


            // 2. Cerrar el socket del servidor (esto rompe el accept() bloqueado)
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ex) {
            log("Error al detener el servidor: " + ex.getMessage());
        }
    }


    /**
     * Recorre las interfaces de red de esta máquina y muestra en el log
     * las IPs (IPv4) que otros computadores de la misma red podrían usar
     * para conectarse a este servidor.
     */
    private void mostrarIpsLocales() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isLoopback() || !ni.isUp()) continue; // ignorar loopback e interfaces caídas
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    // Solo IPv4, para que sea más fácil de escribir en el cliente
                    if (addr.getHostAddress().indexOf(':') == -1) {
                        log("IP disponible para clientes remotos: " + addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ex) {
            log("No se pudieron listar las IPs locales: " + ex.getMessage());
        }
    }


    /**
     * Envía el mensaje recibido a TODOS los clientes conectados
     * (broadcast). Si el envío a algún cliente falla, ese cliente se
     * elimina de la lista.
     */
    private void broadcast(String mensaje, ClientHandler remitente) {
        for (ClientHandler c : clientes) {
            c.enviar(mensaje);
        }
    }


    /**
     * Escribe una línea en el área de texto del servidor. Se usa
     * invokeLater porque los componentes Swing solo deben modificarse
     * desde el hilo de eventos (EDT), y este método puede ser invocado
     * desde los hilos de red.
     */
    private void log(String texto) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(texto + "\n"));
    }


    /**
     * Atiende a un cliente individual en su propio hilo: lee sus
     * mensajes y los retransmite a todos los clientes conectados.
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final String id;
        private BufferedReader in;
        private PrintWriter out;


        ClientHandler(Socket socket, String id) {
            this.socket = socket;
            this.id = id;
        }


        void enviar(String mensaje) {
            if (out != null) {
                out.println(mensaje);
            }
        }


        // Permite cerrar el socket del cliente desde afuera (usado por detenerServidor()).
        void cerrar() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }


        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);


                String linea;
                while ((linea = in.readLine()) != null) {
                    String mensaje = id + ": " + linea;
                    log(mensaje);
                    broadcast(mensaje, this);
                }
            } catch (IOException ex) {
                log(id + " - error de conexión: " + ex.getMessage());
            } finally {
                clientes.remove(this);
                log(id + " se ha desconectado. Clientes conectados: " + clientes.size());
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }


    // Variables declaration - do not modify
    private javax.swing.JButton bIniciar;
    private javax.swing.JButton bDetener;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
}




















