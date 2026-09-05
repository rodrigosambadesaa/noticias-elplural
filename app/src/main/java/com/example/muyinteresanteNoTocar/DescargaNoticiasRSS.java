package com.example.muyinteresanteNoTocar;

import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import com.example.muyinteresante.util.ConnectivityAndInternetAccess;

/* Parsea un canal RSS y devuelve sus items en un ArrayList */

public class DescargaNoticiasRSS extends AsyncTask<String,Integer,ArrayList<NoticiaRSS>>{

	public interface RemoteResultListener {
		void onRemoteResult(RemoteResult result);
	}

	public static final class RemoteResult {
		private final ArrayList<NoticiaRSS> noticias;
		private final Integer httpStatus;
		private final Throwable failure;

		private RemoteResult(
				ArrayList<NoticiaRSS> noticias,
				Integer httpStatus,
				Throwable failure) {
			this.noticias = noticias;
			this.httpStatus = httpStatus;
			this.failure = failure;
		}

		public ArrayList<NoticiaRSS> getNoticias() {
			return noticias;
		}

		public Integer getHttpStatus() {
			return httpStatus;
		}

		public Throwable getFailure() {
			return failure;
		}
	}

	private Context contexto=null;
	private iNoticiaRSS objetoReceptor=null;
	private ProgressDialog pd=null;
	private boolean mostrarProgreso=true;
	private RemoteResultListener remoteResultListener;
	private Integer httpStatus;
	private Throwable failure;
	
	private static final String MENSAJE_PD="Descargando noticias...";
	
	
	public DescargaNoticiasRSS(Context contexto, iNoticiaRSS objetoReceptor){
		this(contexto, objetoReceptor, true);
	}

	/**
	 * Permite reutilizar el descargador para paginación/infinite scroll sin abrir
	 * un ProgressDialog modal cada vez que se solicitan noticias antiguas.
	 */
	public DescargaNoticiasRSS(Context contexto, iNoticiaRSS objetoReceptor, boolean mostrarProgreso){
		this.contexto = contexto;
		this.objetoReceptor = objetoReceptor;
		this.mostrarProgreso = mostrarProgreso;
	}

	public DescargaNoticiasRSS(
			Context contexto,
			RemoteResultListener remoteResultListener,
			boolean mostrarProgreso) {
		this.contexto = contexto;
		this.remoteResultListener = remoteResultListener;
		this.mostrarProgreso = mostrarProgreso;
	}


	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		
		if (contexto != null) {
			// Registramos inicio de intento de conexión para seguimiento de estado
			ConnectivityAndInternetAccess.beginConnectionAttempt(contexto);
		}
		
		if (mostrarProgreso && contexto != null) {
			pd = new ProgressDialog(contexto);
			pd.setMessage(MENSAJE_PD);
			pd.setCancelable(true);
			pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					DescargaNoticiasRSS.this.cancel(true);
				}
			});
			
			pd.show();
		}
	}

	
	@Override
	protected void onCancelled() {
		super.onCancelled();
		
		// Finalizamos intento de conexión
		ConnectivityAndInternetAccess.endConnectionAttempt();
		
		if (pd!=null) pd.dismiss();
	}
	
	 
	@Override							// Recibe URL y nombre Canal RSS.
	protected ArrayList<NoticiaRSS> doInBackground(String... params) {
		
		InputStream entrada = null;
		
		try{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setIgnoringComments(true);
			dbf.setCoalescing(true);
			DocumentBuilder db = dbf.newDocumentBuilder(); 
			
			 // Creamos objeto URL a partir de la direccion web para conectarnos con el servidor
			URL url = new URL(params[0]);
			URLConnection conex = url.openConnection(); // Abrimos la conexion
			conex.setConnectTimeout(10000);
			conex.setReadTimeout(10000);
			conex.setUseCaches(false); // Evitamos la cache de datos.
			conex.setRequestProperty("accept", "application/rss+xml, application/xml, text/xml, */*");
			conex.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) noticias-elplural/1.0");

			if (conex instanceof HttpURLConnection) {
				HttpURLConnection http = (HttpURLConnection) conex;
				http.setInstanceFollowRedirects(true);
				httpStatus = http.getResponseCode();
				if (httpStatus < 200 || httpStatus >= 300) {
					Log.w("DescargaNoticiasRSS", "RSS respondió HTTP " + httpStatus + " para " + params[0]);
					return null;
				}
			}
			 
			// Abrimos el fichero para su lectura/descarga
			entrada = conex.getInputStream();	

			Document arbolXML =db.parse(entrada);
			entrada.close();
			Element raiz = arbolXML.getDocumentElement(); 
			raiz.normalize(); 
			
			ArrayList<NoticiaRSS> noticias = new ArrayList<NoticiaRSS>();
			
			NodeList listaItems = raiz.getElementsByTagName("item");
			
			for (int i=0;i<listaItems.getLength();i++){
				try {
					Element item = (Element)listaItems.item(i);
					noticias.add(new NoticiaRSS(item, params[1]));
					
					publishProgress(noticias.size());
				}
				catch(Exception e){ e.printStackTrace();}
			}
			
			return noticias;
		}
		catch (IOException e){
			failure = e;
			Log.w("DescargaNoticiasRSS", "Fallo de red descargando RSS: " + params[0], e);
			return null;
		}
		catch (Exception e){
			failure = e;
			Log.e("DescargaNoticiasRSS", "Error procesando RSS: " + params[0], e);
			return null;
		}
		finally {
			if (entrada != null) {
				try {
					entrada.close();
				} catch (Exception ignored) { }
			}
		}

	}
	
	
	@Override
	protected void onPostExecute(ArrayList<NoticiaRSS> result) {
		super.onPostExecute(result);
		
		// Finalizamos intento de conexión
		ConnectivityAndInternetAccess.endConnectionAttempt();
		
		if (pd!=null) pd.dismiss();
		if (objetoReceptor!=null ) objetoReceptor.onRecibeNoticiasRSS(result);
		if (remoteResultListener != null) {
			remoteResultListener.onRemoteResult(
					new RemoteResult(result, httpStatus, failure));
		}
	}


	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
		if (pd != null && values != null && values.length > 0) {
			pd.setMessage(MENSAJE_PD + " " + values[0]);
		}
	}
}
