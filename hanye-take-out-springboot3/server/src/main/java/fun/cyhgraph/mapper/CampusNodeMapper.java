package fun.cyhgraph.mapper;

import fun.cyhgraph.entity.CampusNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CampusNodeMapper {

    @Select("select * from campus_node where status = 1 order by id")
    List<CampusNode> listActive();

    @Select("select * from campus_node where status = 1 and node_type = #{nodeType} order by id")
    List<CampusNode> listByType(String nodeType);

    @Select("select * from campus_node where id = #{id}")
    CampusNode getById(Integer id);
}

