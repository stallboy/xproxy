package xproxy.httpp;

public interface Filter {

    void filter(Request request, Response response);

}
